package nl.inl.blacklab.search.indexmetadata;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.Terms;
import org.apache.lucene.util.Bits;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.exceptions.IndexTooOld;
import nl.inl.blacklab.index.DocIndexerFactory.Format;
import nl.inl.blacklab.index.DocumentFormats;
import nl.inl.blacklab.index.Indexer;
import nl.inl.blacklab.index.annotated.AnnotatedFieldWriter;
import nl.inl.blacklab.indexers.config.ConfigAnnotatedField;
import nl.inl.blacklab.indexers.config.ConfigAnnotation;
import nl.inl.blacklab.indexers.config.ConfigCorpus;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.indexers.config.ConfigLinkedDocument;
import nl.inl.blacklab.indexers.config.ConfigMetadataBlock;
import nl.inl.blacklab.indexers.config.ConfigMetadataField;
import nl.inl.blacklab.indexers.config.ConfigMetadataFieldGroup;
import nl.inl.blacklab.indexers.config.ConfigStandoffAnnotations;
import nl.inl.blacklab.indexers.config.TextDirection;
import nl.inl.blacklab.search.BlackLabIndexImpl;
import nl.inl.util.FileUtil;
import nl.inl.util.Json;
import nl.inl.util.StringUtil;

/**
 * Determines the structure of a BlackLab index.
 */
public class IndexMetadataImpl implements IndexMetadata, IndexMetadataWriter {

    private static final Charset INDEX_STRUCT_FILE_ENCODING = Indexer.DEFAULT_INPUT_ENCODING;

    private static final Logger logger = LogManager.getLogger(IndexMetadataImpl.class);

    private static final String METADATA_FILE_NAME = "indexmetadata";

    /**
     * The latest index format. Written to the index metadata file.
     *
     * 3: first version to include index metadata file 3.1: tag length in payload
     */
    private static final String LATEST_INDEX_FORMAT = "3.1";

    /** What keys may occur at top level? */
    private static final Set<String> KEYS_TOP_LEVEL = new HashSet<>(Arrays.asList(
            "displayName", "description", "contentViewable", "textDirection",
            "documentFormat", "tokenCount", "versionInfo", "fieldInfo"));

    /** What keys may occur under versionInfo? */
    private static final Set<String> KEYS_VERSION_INFO = new HashSet<>(Arrays.asList(
            "indexFormat", "blackLabBuildTime", "blackLabVersion", "timeCreated",
            "timeModified", "alwaysAddClosingToken", "tagLengthInPayload"));

    /** What keys may occur under fieldInfo? */
    private static final Set<String> KEYS_FIELD_INFO = new HashSet<>(Arrays.asList(
            "namingScheme", "unknownCondition", "unknownValue",
            "metadataFields", "complexFields", "metadataFieldGroups",
            "defaultAnalyzer", "titleField", "authorField", "dateField", "pidField"));

    /** What keys may occur under metadataFieldGroups group? */
    private static final Set<String> KEYS_METADATA_GROUP = new HashSet<>(Arrays.asList(
            "name", "fields", "addRemainingFields"));

    /** What keys may occur under metadata field config? */
    private static final Set<String> KEYS_META_FIELD_CONFIG = new HashSet<>(Arrays.asList(
            "type", "displayName", "uiType",
            "description", "group", "analyzer",
            "unknownValue", "unknownCondition", "values",
            "displayValues", "displayOrder", "valueListComplete"));

    /** What keys may occur under annotated field config? */
    private static final Set<String> KEYS_ANNOTATED_FIELD_CONFIG = new HashSet<>(Arrays.asList(
            "displayName", "description", "mainProperty",
            "noForwardIndexProps", "displayOrder", "annotations"));

    /** Where to save indexmetadata.json */
    private File indexDir;

    /** Index display name */
    private String displayName;

    /** Index description */
    private String description;

    /** When BlackLab.jar was built */
    private String blackLabBuildTime;

    /** BlackLab version used to (initially) create index */
    private String blackLabVersion;

    /** Format the index uses */
    private String indexFormat;

    /** Time at which index was created */
    private String timeCreated;

    /** Time at which index was created */
    private String timeModified;

    /**
     * May all users freely retrieve the full content of documents, or is that
     * restricted?
     */
    private boolean contentViewable = false;

    /** Text direction for this corpus */
    private TextDirection textDirection = TextDirection.LEFT_TO_RIGHT;

    /**
     * Indication of the document format(s) in this index.
     *
     * This is in the form of a format identifier as understood by the
     * DocumentFormats class (either an abbreviation or a (qualified) class name).
     */
    private String documentFormat;

    private long tokenCount = 0;

    /** When we save this file, should we write it as json or yaml? */
    private boolean saveAsJson = true;

    /** Our metadata fields */
    private MetadataFieldsImpl metadataFields;

    /** Our annotated fields */
    private AnnotatedFieldsImpl annotatedFields;

    /** Is this instance frozen, that is, are all mutations disallowed? */
    private boolean frozen;

    /**
     * Construct an IndexMetadata object, querying the index for the available
     * fields and their types.
     *
     * @param reader the index of which we want to know the structure
     * @param indexDir where the index (and the metadata file) is stored
     * @param createNewIndex whether we're creating a new index
     * @param config input format config to use as template for index structure /
     *            metadata (if creating new index)
     */
    public IndexMetadataImpl(IndexReader reader, File indexDir, boolean createNewIndex, ConfigInputFormat config) {
        this.indexDir = indexDir;

        metadataFields = new MetadataFieldsImpl();
        annotatedFields = new AnnotatedFieldsImpl();

        // Find existing metadata file, if any.
        File metadataFile = FileUtil.findFile(Arrays.asList(indexDir), METADATA_FILE_NAME,
                Arrays.asList("json", "yaml", "yml"));
        if (metadataFile != null && createNewIndex) {
            // Don't leave the old metadata file if we're creating a new index
            if (metadataFile.exists() && !metadataFile.delete())
                throw new BlackLabException("Could not delete file: " + metadataFile);
        }

        // If none found, or creating new index: write a .yaml file.
        if (createNewIndex || metadataFile == null) {
            metadataFile = new File(indexDir, METADATA_FILE_NAME + ".yaml");
        }
        saveAsJson = false;
        if (createNewIndex && config != null) {

            // Create an index metadata file from this config.
            ConfigCorpus corpusConfig = config.getCorpusConfig();
            ObjectMapper mapper = Json.getJsonObjectMapper();
            ObjectNode jsonRoot = mapper.createObjectNode();
            String displayName = corpusConfig.getDisplayName();
            if (displayName.isEmpty())
                displayName = determineIndexName();
            jsonRoot.put("displayName", displayName);
            jsonRoot.put("description", corpusConfig.getDescription());
            jsonRoot.put("contentViewable", corpusConfig.isContentViewable());
            jsonRoot.put("textDirection", corpusConfig.getTextDirection().getCode());
            jsonRoot.put("documentFormat", config.getName());
            addVersionInfo(jsonRoot);
            ObjectNode fieldInfo = jsonRoot.putObject("fieldInfo");
            fieldInfo.put("defaultAnalyzer", config.getMetadataDefaultAnalyzer());
            for (Entry<String, String> e: corpusConfig.getSpecialFields().entrySet()) {
                fieldInfo.put(e.getKey(), e.getValue());
            }
            ArrayNode metaGroups = fieldInfo.putArray("metadataFieldGroups");
            ObjectNode metadata = fieldInfo.putObject("metadataFields");
            ObjectNode annotated = fieldInfo.putObject("complexFields");

            addFieldInfoFromConfig(metadata, annotated, metaGroups, config);
            extractFromJson(jsonRoot, null, true, false);
            save();
        } else {
            // Read existing metadata or create empty new one
            readOrCreateMetadata(reader, createNewIndex, metadataFile, false);
        }
    }

    /**
     * Construct an IndexMetadata object, querying the index for the available
     * fields and their types.
     * 
     * @param reader the index of which we want to know the structure
     * @param indexDir where the index (and the metadata file) is stored
     * @param createNewIndex whether we're creating a new index
     * @param indexTemplateFile JSON file to use as template for index structure /
     *            metadata (if creating new index)
     */
    public IndexMetadataImpl(IndexReader reader, File indexDir, boolean createNewIndex, File indexTemplateFile) {
        this.indexDir = indexDir;

        metadataFields = new MetadataFieldsImpl();
        annotatedFields = new AnnotatedFieldsImpl();

        // Find existing metadata file, if any.
        File metadataFile = FileUtil.findFile(Arrays.asList(indexDir), METADATA_FILE_NAME,
                Arrays.asList("json", "yaml", "yml"));
        if (metadataFile != null && createNewIndex) {
            // Don't leave the old metadata file if we're creating a new index
            if (!metadataFile.delete())
                throw new BlackLabException("Could not delete file: " + metadataFile);
        }

        // If none found, or creating new index: metadata file should be same format as
        // template.
        if (createNewIndex || metadataFile == null) {
            // No metadata file yet, or creating a new index;
            // use same metadata format as the template
            boolean templateIsJson = false;
            if (indexTemplateFile != null && indexTemplateFile.getName().endsWith(".json"))
                templateIsJson = true;
            String templateExt = templateIsJson ? "json" : "yaml";
            if (createNewIndex && metadataFile != null) {
                // We're creating a new index, but also found a previous metadata file.
                // Is it a different format than the template? If so, we would end up
                // with two metadata files, which is confusing and might lead to errors.
                boolean existingIsJson = metadataFile.getName().endsWith(".json");
                if (existingIsJson != templateIsJson) {
                    // Delete the existing, different-format file to avoid confusion.
                    if (!metadataFile.delete())
                        throw new BlackLabException("Could not delete file: " + metadataFile);
                }
            }
            metadataFile = new File(indexDir, METADATA_FILE_NAME + "." + templateExt);
        }
        saveAsJson = metadataFile.getName().endsWith(".json");
        boolean usedTemplate = false;
        if (createNewIndex && indexTemplateFile != null) {
            // Copy the template file to the index dir and read the metadata again.
            try {
                String fileContents = FileUtils.readFileToString(indexTemplateFile, INDEX_STRUCT_FILE_ENCODING);
                FileUtils.write(metadataFile, fileContents, INDEX_STRUCT_FILE_ENCODING);
            } catch (IOException e) {
                throw BlackLabException.wrap(e);
            }
            usedTemplate = true;
        }

        readOrCreateMetadata(reader, createNewIndex, metadataFile, usedTemplate);
    }

    // Methods that read data
    // ------------------------------------------------------------------------------

    private String determineIndexName() {
        String name = indexDir.getName();
        if (name.equals("index"))
            name = indexDir.getAbsoluteFile().getParentFile().getName();
        return name;
    }

    @Override
    public void save() {
        String ext = saveAsJson ? ".json" : ".yaml";
        File metadataFile = new File(indexDir, METADATA_FILE_NAME + ext);
        try {
            boolean isJson = metadataFile.getName().endsWith(".json");
            ObjectMapper mapper = isJson ? Json.getJsonObjectMapper() : Json.getYamlObjectMapper();
            mapper.writeValue(metadataFile, encodeToJson());
        } catch (IOException e) {
            throw BlackLabException.wrap(e);
        }
    }

    /**
     * Encode the index structure to an (in-memory) JSON structure.
     * 
     * @return json structure
     */
    private ObjectNode encodeToJson() {
        ObjectMapper mapper = Json.getJsonObjectMapper();
        ObjectNode jsonRoot = mapper.createObjectNode();
        jsonRoot.put("displayName", displayName);
        jsonRoot.put("description", description);
        jsonRoot.put("contentViewable", contentViewable);
        jsonRoot.put("textDirection", textDirection.getCode());
        jsonRoot.put("documentFormat", documentFormat);
        jsonRoot.put("tokenCount", tokenCount);
        ObjectNode versionInfo = jsonRoot.putObject("versionInfo");
        versionInfo.put("blackLabBuildTime", blackLabBuildTime);
        versionInfo.put("blackLabVersion", blackLabVersion);
        versionInfo.put("indexFormat", indexFormat);
        versionInfo.put("timeCreated", timeCreated);
        versionInfo.put("timeModified", timeModified);
        versionInfo.put("alwaysAddClosingToken", true); // Indicates that we always index words+1 tokens (last token is
                                                        // for XML tags after the last word)
        versionInfo.put("tagLengthInPayload", true); // Indicates that start tag annotation payload contains tag lengths,
                                                     // and there is no end tag annotation

        ObjectNode fieldInfo = jsonRoot.putObject("fieldInfo");
        fieldInfo.put("namingScheme",
                AnnotatedFieldNameUtil.avoidSpecialCharsInFieldNames() ? "NO_SPECIAL_CHARS" : "DEFAULT");
        fieldInfo.put("defaultAnalyzer", metadataFields.defaultAnalyzerName());
        if (metadataFields.titleField() != null)
            fieldInfo.put("titleField", metadataFields.titleField().name());
        if (metadataFields.authorField() != null)
            fieldInfo.put("authorField", metadataFields.authorField().name());
        if (metadataFields.dateField() != null)
            fieldInfo.put("dateField", metadataFields.dateField().name());
        if (metadataFields.pidField() != null)
            fieldInfo.put("pidField", metadataFields.pidField().name());
        ArrayNode metadataFieldGroups = fieldInfo.putArray("metadataFieldGroups");
        ObjectNode metadataFields = fieldInfo.putObject("metadataFields");
        ObjectNode jsonAnnotatedFields = fieldInfo.putObject("complexFields");

        // Add metadata field group info
        for (MetadataFieldGroup g: metadataFields().groups()) {
            ObjectNode group = metadataFieldGroups.addObject();
            group.put("name", g.name());
            if (g.addRemainingFields())
                group.put("addRemainingFields", true);
            ArrayNode arr = group.putArray("fields");
            Json.arrayOfStrings(arr, g.stream().map(f -> f.name()).collect(Collectors.toList()));
        }

        // Add metadata field info
        for (MetadataField f: this.metadataFields) {
            UnknownCondition unknownCondition = f.unknownCondition();
            ObjectNode fi = metadataFields.putObject(f.name());
            fi.put("displayName", f.displayName());
            fi.put("uiType", f.uiType());
            fi.put("description", f.description());
            fi.put("type", f.type().stringValue());
            fi.put("analyzer", f.analyzerName());
            fi.put("unknownValue", f.unknownValue());
            fi.put("unknownCondition", unknownCondition.toString());
            if (f.isValueListComplete() != ValueListComplete.UNKNOWN)
                fi.put("valueListComplete", f.isValueListComplete().equals(ValueListComplete.YES));
            Map<String, Integer> values = f.valueDistribution();
            if (values != null) {
                ObjectNode jsonValues = fi.putObject("values");
                for (Map.Entry<String, Integer> e: values.entrySet()) {
                    jsonValues.put(e.getKey(), e.getValue());
                }
            }
            Map<String, String> displayValues = f.displayValues();
            if (displayValues != null) {
                ObjectNode jsonDisplayValues = fi.putObject("displayValues");
                for (Map.Entry<String, String> e: displayValues.entrySet()) {
                    jsonDisplayValues.put(e.getKey(), e.getValue());
                }
            }
            List<String> displayOrder = f.displayOrder();
            if (displayOrder != null) {
                ArrayNode jsonDisplayValues = fi.putArray("displayOrder");
                for (String value: displayOrder) {
                    jsonDisplayValues.add(value);
                }
            }
        }

        // Add annotated field info
        for (AnnotatedField f: annotatedFields) {
            ObjectNode fieldInfo2 = jsonAnnotatedFields.putObject(f.name());
            fieldInfo2.put("displayName", f.displayName());
            fieldInfo2.put("description", f.description());
            fieldInfo2.put("mainProperty", f.annotations().main().name());
            ArrayNode arr = fieldInfo2.putArray("displayOrder");
            Json.arrayOfStrings(arr, ((AnnotatedFieldImpl) f).getDisplayOrder());
            ArrayNode annots = fieldInfo2.putArray("annotations");
            for (Annotation annotation: f.annotations()) {
                ObjectNode annot = annots.addObject();
                annot.put("name", annotation.name());
                annot.put("displayName", annotation.displayName());
                annot.put("description", annotation.description());
                annot.put("uiType", annotation.uiType());
            }
        }

        return jsonRoot;

    }

    @Override
    public AnnotatedFields annotatedFields() {
        return annotatedFields;
    }

    /**
     * Detect type by finding the first document that includes this field and
     * inspecting the Fieldable. This assumes that the field type is the same for
     * all documents.
     *
     * @param fieldName the field name to determine the type for
     * @return type of the field (text or numeric)
     */
    @SuppressWarnings("static-method")
    private FieldType getFieldType(String fieldName) {

        /* NOTE: detecting the field type does not work well.
         * Querying values and deciding based on those is not the right way
         * (you can index ints as text too, after all). Lucene does not
         * store the information in the index (and querying the field type does
         * not return an IntField, DoubleField or such. In effect, it expects
         * the client to know.
         *
         * We have a simple, bad approach based on field name below.
         * The "right way" to do it is to keep a schema of field types during
         * indexing.
         */

        FieldType type = FieldType.TOKENIZED;
        if (fieldName.endsWith("Numeric") || fieldName.endsWith("Num"))
            type = FieldType.NUMERIC;
        return type;
    }

    /**
     * Check if a Lucene field has offsets stored.
     *
     * @param reader our index
     * @param luceneFieldName field to check
     * @return true iff field has offsets
     */
    static boolean hasOffsets(IndexReader reader, String luceneFieldName) {
        // Iterate over documents in the index until we find a annotation
        // for this annotated field that has stored character offsets. This is
        // our main annotation.

        // Note that we can't simply retrieve the field from a document and
        // check the FieldType to see if it has offsets or not, as that information
        // is incorrect at search time (always set to false, even if it has offsets).

        Bits liveDocs = MultiFields.getLiveDocs(reader);
        for (int n = 0; n < reader.maxDoc(); n++) {
            if (liveDocs == null || liveDocs.get(n)) {
                try {
                    Terms terms = reader.getTermVector(n, luceneFieldName);
                    if (terms == null) {
                        // No term vector; probably not stored in this document.
                        continue;
                    }
                    if (terms.hasOffsets()) {
                        // This field has offsets stored. Must be the main alternative.
                        return true;
                    }
                    // This alternative has no offsets stored. Don't look at any more
                    // documents, go to the next alternative.
                    break;
                } catch (IOException e) {
                    throw BlackLabException.wrap(e);
                }
            }
        }
        return false;
    }

    @Override
    public MetadataFields metadataFields() {
        return metadataFields;
    }

    /**
     * Get the display name for the index.
     *
     * If no display name was specified, returns the name of the index directory.
     *
     * @return the display name
     */
    @Override
    public String displayName() {
        String dispName = "index";
        if (displayName != null && displayName.length() != 0)
            dispName = displayName;
        if (dispName.equalsIgnoreCase("index"))
            dispName = StringUtils.capitalize(indexDir.getName());
        if (dispName.equalsIgnoreCase("index"))
            dispName = StringUtils.capitalize(indexDir.getAbsoluteFile().getParentFile().getName());
        return dispName;
    }

    /**
     * Get a description of the index, if specified
     * 
     * @return the description
     */
    @Override
    public String description() {
        return description;
    }

    /**
     * Is the content freely viewable by all users, or is it restricted?
     * 
     * @return true if the full content may be retrieved by anyone
     */
    @Override
    public boolean contentViewable() {
        return contentViewable;
    }

    /**
     * What's the text direction of this corpus?
     * 
     * @return text direction
     */
    @Override
    public TextDirection textDirection() {
        return textDirection;
    }

    /**
     * What format(s) is/are the documents in?
     *
     * This is in the form of a format identifier as understood by the
     * DocumentFormats class (either an abbreviation or a (qualified) class name).
     *
     * @return the document format(s)
     */
    @Override
    public String documentFormat() {
        return documentFormat;
    }

    /**
     * What version of the index format is this?
     * 
     * @return the index format version
     */
    @Override
    public String indexFormat() {
        return indexFormat;
    }

    /**
     * When was this index created?
     * 
     * @return date/time stamp
     */
    @Override
    public String timeCreated() {
        return timeCreated;
    }

    /**
     * When was this index last modified?
     * 
     * @return date/time stamp
     */
    @Override
    public String timeModified() {
        return timeCreated;
    }

    /**
     * When was the BlackLab.jar used for indexing built?
     * 
     * @return date/time stamp
     */
    @Override
    public String indexBlackLabBuildTime() {
        return blackLabBuildTime;
    }

    /**
     * When was the BlackLab.jar used for indexing built?
     * 
     * @return date/time stamp
     */
    @Override
    public String indexBlackLabVersion() {
        return blackLabVersion;
    }

    /**
     * Is this a new, empty index?
     *
     * An empty index is one that doesn't have a main contents field yet, or has a
     * main contents field but no indexed tokens yet.
     *
     * @return true if it is, false if not.
     */
    @Override
    public boolean isNewIndex() {
        return annotatedFields.main() == null || tokenCount == 0;
    }

    @Override
    public long tokenCount() {
        return tokenCount;
    }

    /**
     * Format the current date and time according to the SQL datetime convention.
     *
     * @return a string representation, e.g. "1980-02-01 00:00:00"
     */
    static String timestamp() {
        // NOTE: DateFormat is not threadsafe, so we just create a new one every time.
        DateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return dateTimeFormat.format(new Date());
    }

    // Methods that mutate data
    // ------------------------------------

    /**
     * Extract the index structure from the (in-memory) JSON structure (and Lucene
     * index).
     *
     * Looks at the Lucene index to detect certain information (sometimes) missing
     * from the JSON structure, such as naming scheme and available annotations and
     * alternatives for annotated fields. (Should probably eventually all be recorded
     * in the metadata.)
     *
     * @param jsonRoot JSON structure to extract
     * @param reader index reader used to detect certain information, or null if we
     *            don't have an index reader (e.g. because we're creating a new
     *            index)
     * @param usedTemplate whether the JSON structure was read from a indextemplate
     *            file. If so, clear certain parts of it that aren't relevant
     *            anymore.
     * @param initTimestamps whether or not to update blacklab build time, version,
     *            and index creation/modification time
     */
    private void extractFromJson(ObjectNode jsonRoot, IndexReader reader, boolean usedTemplate,
            boolean initTimestamps) {
        ensureNotFrozen();

        // Read and interpret index metadata file
        warnUnknownKeys("at top-level", jsonRoot, KEYS_TOP_LEVEL);
        displayName = Json.getString(jsonRoot, "displayName", "");
        description = Json.getString(jsonRoot, "description", "");
        contentViewable = Json.getBoolean(jsonRoot, "contentViewable", false);
        textDirection = TextDirection.fromCode(Json.getString(jsonRoot, "textDirection", "ltr"));
        documentFormat = Json.getString(jsonRoot, "documentFormat", "");
        tokenCount = Json.getLong(jsonRoot, "tokenCount", 0);

        ObjectNode versionInfo = Json.getObject(jsonRoot, "versionInfo");
        warnUnknownKeys("in versionInfo", versionInfo, KEYS_VERSION_INFO);
        indexFormat = Json.getString(versionInfo, "indexFormat", "");
        if (initTimestamps) {
            blackLabBuildTime = BlackLabIndexImpl.getBlackLabBuildTime();
            blackLabVersion = BlackLabIndexImpl.getBlackLabVersion();
            timeModified = timeCreated = IndexMetadataImpl.timestamp();
        } else {
            blackLabBuildTime = Json.getString(versionInfo, "blackLabBuildTime", "UNKNOWN");
            blackLabVersion = Json.getString(versionInfo, "blackLabVersion", "UNKNOWN");
            timeCreated = Json.getString(versionInfo, "timeCreated", "");
            timeModified = Json.getString(versionInfo, "timeModified", timeCreated);
        }
        boolean alwaysHasClosingToken = Json.getBoolean(versionInfo, "alwaysAddClosingToken", false);
        if (!alwaysHasClosingToken)
            throw new IndexTooOld(
                    "Your index is too old (alwaysAddClosingToken == false). Please use v1.7.1 or re-index your data.");
        boolean tagLengthInPayload = Json.getBoolean(versionInfo, "tagLengthInPayload", false);
        if (!tagLengthInPayload)
            throw new IndexTooOld(
                    "Your index is too old (alwaysAddClosingToken == false). Please use v1.7.1 or re-index your data.");

        // Specified in index metadata file?
        String namingScheme;
        ObjectNode fieldInfo = Json.getObject(jsonRoot, "fieldInfo");
        warnUnknownKeys("in fieldInfo", fieldInfo, KEYS_FIELD_INFO);
        FieldInfos fis = reader == null ? null : MultiFields.getMergedFieldInfos(reader);
        if (fieldInfo.has("namingScheme")) {
            // Yes.
            namingScheme = fieldInfo.get("namingScheme").textValue();
            if (!namingScheme.equals("DEFAULT") && !namingScheme.equals("NO_SPECIAL_CHARS")) {
                throw new BlackLabException("Unknown value for namingScheme: " + namingScheme);
            }
            if (!namingScheme.equals("DEFAULT"))
                logger.error("non-default namingScheme setting found, but this is no longer supported");
        } else {
            // Not specified; detect it.
            boolean hasNoFieldsYet = fis == null || fis.size() == 0;
            boolean usingSpecialCharsAsSeparators = hasNoFieldsYet;
            boolean usingCharacterCodesAsSeparators = false;
            if (fis != null) {
                for (int i1 = 0; i1 < fis.size(); i1++) {
                    FieldInfo fi = fis.fieldInfo(i1);
                    String name1 = fi.name;
                    if (name1.contains("%") || name1.contains("@") || name1.contains("#")) {
                        usingSpecialCharsAsSeparators = true;
                    }
                    if (name1.contains("_PR_") || name1.contains("_AL_") || name1.contains("_BK_")) {
                        usingCharacterCodesAsSeparators = true;
                    }
                }
            }
            if (usingCharacterCodesAsSeparators)
                throw new BlackLabException(
                        "Your index uses _PR_, _AL_, _BK_ as separators (namingScheme). This is no longer supported. Use version 1.7.1 or re-index your data..");
            if (!usingSpecialCharsAsSeparators && !usingCharacterCodesAsSeparators) {
                throw new BlackLabException(
                        "Could not detect index naming scheme. If your index was created with an old version of " +
                                "BlackLab, it may use the old naming scheme and cannot be opened with this version. " +
                                "Please re-index your data, or use a BlackLab version from before August 2014.");
            }
        }
        metadataFields.setDefaultUnknownCondition(Json.getString(fieldInfo, "unknownCondition", "NEVER"));
        metadataFields.setDefaultUnknownValue(Json.getString(fieldInfo, "unknownValue", "unknown"));

        ObjectNode metaFieldConfigs = Json.getObject(fieldInfo, "metadataFields");
        boolean hasMetaFields = metaFieldConfigs.size() > 0;
        ObjectNode annotatedFieldConfigs = Json.getObject(fieldInfo, "complexFields");
        boolean hasAnnotatedFields = annotatedFieldConfigs.size() > 0;
        boolean hasFieldInfo = hasMetaFields || hasAnnotatedFields;

        if (hasFieldInfo && fieldInfo.has("metadataFieldGroups")) {
            metadataFields.clearMetadataGroups();
            JsonNode groups = fieldInfo.get("metadataFieldGroups");
            for (int i = 0; i < groups.size(); i++) {
                JsonNode group = groups.get(i);
                warnUnknownKeys("in metadataFieldGroup", group, KEYS_METADATA_GROUP);
                String name = Json.getString(group, "name", "UNKNOWN");
                List<String> fields = Json.getListOfStrings(group, "fields");
                boolean addRemainingFields = Json.getBoolean(group, "addRemainingFields", false);
                MetadataFieldGroupImpl metadataGroup = new MetadataFieldGroupImpl(metadataFields(), name, fields,
                        addRemainingFields);
                metadataFields.putMetadataGroup(name, metadataGroup);
            }
        }
        if (hasFieldInfo) {
            // Metadata fields
            Iterator<Entry<String, JsonNode>> it = metaFieldConfigs.fields();
            while (it.hasNext()) {
                Entry<String, JsonNode> entry = it.next();
                String fieldName = entry.getKey();
                JsonNode fieldConfig = entry.getValue();
                warnUnknownKeys("in metadata field config for '" + fieldName + "'", fieldConfig,
                        KEYS_META_FIELD_CONFIG);
                FieldType fieldType = FieldType.fromStringValue(Json.getString(fieldConfig, "type", "tokenized"));
                MetadataFieldImpl fieldDesc = new MetadataFieldImpl(fieldName, fieldType);
                fieldDesc.setDisplayName(Json.getString(fieldConfig, "displayName", fieldName));
                fieldDesc.setUiType(Json.getString(fieldConfig, "uiType", ""));
                fieldDesc.setDescription(Json.getString(fieldConfig, "description", ""));
                fieldDesc.setGroup(Json.getString(fieldConfig, "group", ""));
                fieldDesc.setAnalyzer(Json.getString(fieldConfig, "analyzer", "DEFAULT"));
                fieldDesc.setUnknownValue(
                        Json.getString(fieldConfig, "unknownValue", metadataFields.defaultUnknownValue()));
                UnknownCondition unk = UnknownCondition
                        .fromStringValue(Json.getString(fieldConfig, "unknownCondition",
                                metadataFields.defaultUnknownCondition()));
                fieldDesc.setUnknownCondition(unk);
                if (fieldConfig.has("values"))
                    fieldDesc.setValues(fieldConfig.get("values"));
                if (fieldConfig.has("displayValues"))
                    fieldDesc.setDisplayValues(fieldConfig.get("displayValues"));
                if (fieldConfig.has("displayOrder"))
                    fieldDesc.setDisplayOrder(Json.getListOfStrings(fieldConfig, "displayOrder"));
                if (fieldConfig.has("valueListComplete"))
                    fieldDesc.setValueListComplete(Json.getBoolean(fieldConfig, "valueListComplete", false));
                metadataFields.put(fieldName, fieldDesc);
            }

            // Annotated fields
            it = annotatedFieldConfigs.fields();
            while (it.hasNext()) {
                Entry<String, JsonNode> entry = it.next();
                String fieldName = entry.getKey();
                JsonNode fieldConfig = entry.getValue();
                warnUnknownKeys("in annotated field config for '" + fieldName + "'", fieldConfig,
                        KEYS_ANNOTATED_FIELD_CONFIG);
                AnnotatedFieldImpl fieldDesc = new AnnotatedFieldImpl(fieldName);
                fieldDesc.setDisplayName(Json.getString(fieldConfig, "displayName", fieldName));
                fieldDesc.setDescription(Json.getString(fieldConfig, "description", ""));
                String mainAnnotationName = Json.getString(fieldConfig, "mainProperty", "");
                if (mainAnnotationName.length() > 0)
                    fieldDesc.setMainAnnotationName(mainAnnotationName);

                // Process information about annotations (displayName, uiType, etc.
                ArrayList<String> annotationOrder = new ArrayList<>();
                if (fieldConfig.has("annotations")) {
                    JsonNode annotations = fieldConfig.get("annotations");
                    Iterator<JsonNode> itAnnot = annotations.elements();
                    while (itAnnot.hasNext()) {
                        JsonNode jsonAnnotation = itAnnot.next();
                        Iterator<Entry<String, JsonNode>> itAnnotOpt = jsonAnnotation.fields();
                        AnnotationImpl annotation = new AnnotationImpl(fieldDesc);
                        while (itAnnotOpt.hasNext()) {
                            Entry<String, JsonNode> opt = itAnnotOpt.next();
                            switch (opt.getKey()) {
                            case "name":
                                annotation.setName(opt.getValue().textValue());
                                annotationOrder.add(opt.getValue().textValue());
                                break;
                            case "displayName":
                                annotation.setDisplayName(opt.getValue().textValue());
                                break;
                            case "description":
                                annotation.setDescription(opt.getValue().textValue());
                                break;
                            case "uiType":
                                annotation.setUiType(opt.getValue().textValue());
                                break;
                            default:
                                logger.warn("Unknown key " + opt.getKey() + " in annotation for field '" + fieldName
                                        + "' in indexmetadata file");
                                break;
                            }
                        }
                        if (StringUtils.isEmpty(annotation.name()))
                            logger.warn("Annotation entry without name for field '" + fieldName
                                    + "' in indexmetadata file; skipping");
                        else
                            fieldDesc.putAnnotation(annotation);
                    }
                }

                // These annotations should get no forward index
                // TODO: refactor this so this information is stored with each annotation instead,
                // deprecating this setting
                JsonNode nodeNoForwardIndexAnnotations = fieldConfig.get("noForwardIndexProps");
                if (nodeNoForwardIndexAnnotations instanceof ArrayNode) {
                    Iterator<JsonNode> itNFIP = nodeNoForwardIndexAnnotations.elements();
                    Set<String> noForwardIndex = new HashSet<>();
                    while (itNFIP.hasNext()) {
                        noForwardIndex.add(itNFIP.next().asText());
                    }
                    fieldDesc.setNoForwardIndexAnnotations(noForwardIndex);
                } else {
                    String noForwardIndex = Json.getString(fieldConfig, "noForwardIndexProps", "").trim();
                    if (noForwardIndex.length() > 0) {
                        String[] noForwardIndexAnnotations = noForwardIndex.split("\\s+");
                        fieldDesc.setNoForwardIndexAnnotations(new HashSet<>(Arrays.asList(noForwardIndexAnnotations)));
                    }
                }

                // This is the "natural order" of our annotations
                // (probably not needed anymore - if not specified, the order of the annotations
                // will be used)
                List<String> displayOrder = Json.getListOfStrings(fieldConfig, "displayOrder");
                if (displayOrder.isEmpty()) {
                    displayOrder.addAll(annotationOrder);
                }
                fieldDesc.setDisplayOrder(displayOrder);

                annotatedFields.put(fieldName, fieldDesc);
            }
        }
        if (fis != null) {
            // Detect fields
            for (int i = 0; i < fis.size(); i++) {
                FieldInfo fi = fis.fieldInfo(i);
                String name = fi.name;

                // Parse the name to see if it is a metadata field or part of an annotated field.
                String[] parts;
                if (name.endsWith("Numeric")) {
                    // Special case: this is not a annotation alternative, but a numeric
                    // alternative for a metadata field.
                    // (TODO: this should probably be changed or removed)
                    parts = new String[] { name };
                } else {
                    parts = AnnotatedFieldNameUtil.getNameComponents(name);
                }
                if (parts.length == 1 && !annotatedFields.exists(parts[0])) {
                    if (!metadataFields.exists(name)) {
                        // Metadata field, not found in metadata JSON file
                        FieldType type = getFieldType(name);
                        MetadataFieldImpl metadataFieldDesc = new MetadataFieldImpl(name, type);
                        metadataFieldDesc
                                .setUnknownCondition(
                                        UnknownCondition.fromStringValue(metadataFields.defaultUnknownCondition()));
                        metadataFieldDesc.setUnknownValue(metadataFields.defaultUnknownValue());
                        metadataFields.put(name, metadataFieldDesc);
                    }
                } else {
                    // Part of annotated field.
                    if (metadataFields.exists(parts[0])) {
                        throw new BlackLabException(
                                "Annotated field and metadata field with same name, error! ("
                                        + parts[0] + ")");
                    }

                    // Get or create descriptor object.
                    AnnotatedFieldImpl cfd = getOrCreateAnnotatedField(parts[0]);
                    cfd.processIndexField(parts);
                }
            } // even if we have metadata, we still have to detect annotations/sensitivities
        }

        metadataFields.setDefaultAnalyzerName(Json.getString(fieldInfo, "defaultAnalyzer", "DEFAULT"));

        metadataFields.clearSpecialFields();
        if (fieldInfo.has("titleField"))
            metadataFields.setSpecialField(MetadataFields.TITLE, fieldInfo.get("titleField").textValue());
        if (metadataFields.titleField() == null) {
            MetadataField titleField = metadataFields.findTextField("title");
            metadataFields.setSpecialField(MetadataFields.TITLE, titleField == null ? null : titleField.name());
            if (metadataFields.titleField() == null) {
                metadataFields.setSpecialField(MetadataFields.TITLE, "fromInputFile");
            }
        }
        if (fieldInfo.has("authorField"))
            metadataFields.setSpecialField(MetadataFields.AUTHOR, fieldInfo.get("authorField").textValue());
        if (fieldInfo.has("dateField"))
            metadataFields.setSpecialField(MetadataFields.DATE, fieldInfo.get("dateField").textValue());
        if (fieldInfo.has("pidField"))
            metadataFields.setSpecialField(MetadataFields.PID, fieldInfo.get("pidField").textValue());

        if (usedTemplate) {
            // Update / clear possible old values that were in the template file
            // (template file may simply be the metadata file copied from a previous
            // version)

            // Reset version info
            blackLabBuildTime = BlackLabIndexImpl.getBlackLabBuildTime();
            blackLabVersion = BlackLabIndexImpl.getBlackLabVersion();
            indexFormat = LATEST_INDEX_FORMAT;
            timeModified = timeCreated = IndexMetadataImpl.timestamp();

            // Clear any recorded values in metadata fields
            metadataFields.resetForIndexing();
        }
    }

    private void readOrCreateMetadata(IndexReader reader, boolean createNewIndex, File metadataFile,
            boolean usedTemplate) {
        ensureNotFrozen();

        // Read and interpret index metadata file
        if ((createNewIndex && !usedTemplate) || !metadataFile.exists()) {
            // No metadata file yet; start with a blank one
            ObjectMapper mapper = Json.getJsonObjectMapper();
            ObjectNode jsonRoot = mapper.createObjectNode();
            jsonRoot.put("displayName", determineIndexName());
            jsonRoot.put("description", "");
            addVersionInfo(jsonRoot);
            ObjectNode fieldInfo = jsonRoot.putObject("fieldInfo");
            fieldInfo.putObject("metadataFields");
            fieldInfo.putObject("complexFields");
            extractFromJson(jsonRoot, reader, false, false);
        } else {
            // Read the metadata file
            try {
                boolean isJson = metadataFile.getName().endsWith(".json");
                ObjectMapper mapper = isJson ? Json.getJsonObjectMapper() : Json.getYamlObjectMapper();
                ObjectNode jsonRoot = (ObjectNode) mapper.readTree(metadataFile);
                extractFromJson(jsonRoot, reader, usedTemplate, false);
            } catch (IOException e) {
                throw BlackLabException.wrap(e);
            }
        }

        // Detect main contents field and main annotations of annotated fields
        if (!createNewIndex) { // new index doesn't have this information yet
            // Detect the main annotations for all annotated fields
            // (looks for fields with char offset information stored)
            AnnotatedFieldImpl mainContentsField = null;
            for (AnnotatedField d: annotatedFields) {
                if (mainContentsField == null || d.name().equals("contents"))
                    mainContentsField = (AnnotatedFieldImpl) d;
                if (tokenCount > 0) // no use trying this on an empty index
                    ((AnnotatedFieldImpl) d).detectMainAnnotation(reader);
            }
            annotatedFields.setMainContentsField(mainContentsField);
        }
    }

    private AnnotatedFieldImpl getOrCreateAnnotatedField(String name) {
        ensureNotFrozen();
        AnnotatedFieldImpl cfd = null;
        if (annotatedFields.exists(name))
            cfd = ((AnnotatedFieldImpl) annotatedFields().get(name));
        if (cfd == null) {
            cfd = new AnnotatedFieldImpl(name);
            annotatedFields.put(name, cfd);
        }
        return cfd;
    }

    /**
     * Indicate that the index was modified, so that fact will be recorded in the
     * metadata file.
     */
    @Override
    public void updateLastModified() {
        // TODO: make sure this method is called when adding documents to index!
        ensureNotFrozen();
        timeModified = IndexMetadataImpl.timestamp();
    }

    /**
     * While indexing, check if an annotated field is already registered in the
     * metadata, and if not, add it now.
     *
     * @param fieldWriter field to register
     * @return registered annotated field
     */
    @Override
    public AnnotatedField registerAnnotatedField(AnnotatedFieldWriter fieldWriter) {
        ensureNotFrozen();
        
        String fieldName = fieldWriter.getName();
        String mainAnnotName = fieldWriter.getMainAnnotation().getName();
        
        if (annotatedFields.exists(fieldName))
            return annotatedFields.get(fieldName);
        // Not registered yet; do so now. Note that we only add the main annotation,
        // not the other annotations, but that's okay; they're not needed at index
        // time and will be detected at search time.
        AnnotatedFieldImpl cf = getOrCreateAnnotatedField(fieldName);
        cf.getOrCreateAnnotation(mainAnnotName); // create main annotation
        cf.setMainAnnotationName(mainAnnotName); // set main annotation
        fieldWriter.setAnnotatedField(cf);
        return cf;
    }

    @Override
    public MetadataField registerMetadataField(String fieldName) {
        ensureNotFrozen();
        return metadataFields.register(fieldName);
    }

    /**
     * Set the display name for this index. Only makes sense in index mode where the
     * change will be saved. Usually called when creating an index.
     *
     * @param displayName the display name to set.
     */
    @Override
    public void setDisplayName(String displayName) {
        ensureNotFrozen();
        if (displayName.length() > 80)
            displayName = StringUtil.abbreviate(displayName, 75);
        this.displayName = displayName;
    }

    /**
     * Set a document format (or formats) for this index.
     *
     * This should be a format identifier as understood by the DocumentFormats class
     * (either an abbreviation or a (qualified) class name).
     *
     * It only makes sense to call this in index mode, where this change will be
     * saved.
     *
     * @param documentFormat the document format to store
     */
    @Override
    public void setDocumentFormat(String documentFormat) {
        ensureNotFrozen();
        this.documentFormat = documentFormat;
    }

    @Override
    public void addToTokenCount(long tokensProcessed) {
        ensureNotFrozen();
        tokenCount += tokensProcessed;
    }

    /**
     * Used when creating an index to initialize contentViewable setting. Do not use
     * otherwise.
     *
     * It is also used to support a deprecated configuration setting in BlackLab
     * Server, but this use will eventually be removed.
     *
     * @param contentViewable whether content may be freely viewed
     */
    @Override
    public void setContentViewable(boolean contentViewable) {
        ensureNotFrozen();
        this.contentViewable = contentViewable;
    }

    /**
     * Used when creating an index to initialize textDirection setting. Do not use
     * otherwise.
     *
     * @param textDirection text direction
     */
    @Override
    public void setTextDirection(TextDirection textDirection) {
        ensureNotFrozen();
        this.textDirection = textDirection;
    }

    /**
     * If the object node contains any keys other than those specified, warn about
     * it
     *
     * @param where where are we in the file (e.g. "top level", "annotated field
     *            'contents'", etc.)
     * @param node node to check
     * @param knownKeys keys that may occur under this node
     */
    private static void warnUnknownKeys(String where, JsonNode node, Set<String> knownKeys) {
        Iterator<Entry<String, JsonNode>> it = node.fields();
        while (it.hasNext()) {
            Entry<String, JsonNode> e = it.next();
            String key = e.getKey();
            if (!knownKeys.contains(key))
                logger.warn("Unknown key " + key + " " + where + " in indexmetadata file");
        }
    }

    private static void addVersionInfo(ObjectNode jsonRoot) {
        ObjectNode versionInfo = jsonRoot.putObject("versionInfo");
        versionInfo.put("blackLabBuildTime", BlackLabIndexImpl.getBlackLabBuildTime());
        versionInfo.put("blackLabVersion", BlackLabIndexImpl.getBlackLabVersion());
        versionInfo.put("timeCreated", IndexMetadataImpl.timestamp());
        versionInfo.put("timeModified", IndexMetadataImpl.timestamp());
        versionInfo.put("indexFormat", IndexMetadataImpl.LATEST_INDEX_FORMAT);
        versionInfo.put("alwaysAddClosingToken", true); // always true, but BL check for it, so required
        versionInfo.put("tagLengthInPayload", true); // always true, but BL check for it, so required
    }

    private void addFieldInfoFromConfig(ObjectNode metadata, ObjectNode annotated, ArrayNode metaGroups,
            ConfigInputFormat config) {
        ensureNotFrozen();

        // Add metadata field groups info
        ConfigCorpus corpusConfig = config.getCorpusConfig();
        for (ConfigMetadataFieldGroup g: corpusConfig.getMetadataFieldGroups().values()) {
            ObjectNode h = metaGroups.addObject();
            h.put("name", g.getName());
            if (g.getFields().size() > 0) {
                ArrayNode i = h.putArray("fields");
                for (String f: g.getFields()) {
                    i.add(f);
                }
            }
            if (g.isAddRemainingFields())
                h.put("addRemainingFields", true);
        }

        // Add metadata info
        String defaultAnalyzer = config.getMetadataDefaultAnalyzer();
        for (ConfigMetadataBlock b: config.getMetadataBlocks()) {
            for (ConfigMetadataField f: b.getFields()) {
                if (f.isForEach())
                    continue;
                ObjectNode g = metadata.putObject(f.getName());
                g.put("displayName", f.getDisplayName());
                g.put("description", f.getDescription());
                g.put("type", f.getType().stringValue());
                if (!f.getAnalyzer().equals(defaultAnalyzer))
                    g.put("analyzer", f.getAnalyzer());
                g.put("uiType", f.getUiType());
                g.put("unknownCondition", f.getUnknownCondition().stringValue());
                g.put("unknownValue", f.getUnknownValue());
                ObjectNode h = g.putObject("displayValues");
                for (Entry<String, String> e: f.getDisplayValues().entrySet()) {
                    h.put(e.getKey(), e.getValue());
                }
                ArrayNode i = g.putArray("displayOrder");
                for (String v: f.getDisplayOrder()) {
                    i.add(v);
                }
            }
        }

        // Add annotated field info
        for (ConfigAnnotatedField f: config.getAnnotatedFields().values()) {
            ObjectNode g = annotated.putObject(f.getName());
            g.put("displayName", f.getDisplayName());
            g.put("description", f.getDescription());
            g.put("mainProperty", f.getAnnotations().values().iterator().next().getName());
            ArrayNode displayOrder = g.putArray("displayOrder");
            ArrayNode noForwardIndexAnnotations = g.putArray("noForwardIndexProps");
            ArrayNode annotations = g.putArray("annotations");
            for (ConfigAnnotation a: f.getAnnotations().values()) {
                displayOrder.add(a.getName());
                if (!a.createForwardIndex())
                    noForwardIndexAnnotations.add(a.getName());
                ObjectNode annotation = annotations.addObject();
                annotation.put("name", a.getName());
                annotation.put("displayName", a.getDisplayName());
                annotation.put("description", a.getDescription());
                annotation.put("uiType", a.getUiType());
            }
            for (ConfigStandoffAnnotations standoff: f.getStandoffAnnotations()) {
                for (ConfigAnnotation a: standoff.getAnnotations().values()) {
                    displayOrder.add(a.getName());
                    if (!a.createForwardIndex())
                        noForwardIndexAnnotations.add(a.getName());
                }
            }
        }

        // Also (recursively) add metadata and annotated field config from any linked
        // documents
        for (ConfigLinkedDocument ld: config.getLinkedDocuments().values()) {
            Format format = DocumentFormats.getFormat(ld.getInputFormatIdentifier());
            if (format.isConfigurationBased())
                addFieldInfoFromConfig(metadata, annotated, metaGroups, format.getConfig());
        }
    }

    @Override
    public IndexMetadata freeze() {
        this.frozen = true;
        annotatedFields.freeze();
        metadataFields.freeze();
        return this;
    }

    @Override
    public boolean isFrozen() {
        return this.frozen;
    }

}