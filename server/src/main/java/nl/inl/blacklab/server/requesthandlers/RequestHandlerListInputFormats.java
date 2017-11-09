package nl.inl.blacklab.server.requesthandlers;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.index.DocumentFormats;
import nl.inl.blacklab.index.DocumentFormats.FormatDesc;
import nl.inl.blacklab.index.config.ConfigAnnotatedField;
import nl.inl.blacklab.index.config.ConfigAnnotation;
import nl.inl.blacklab.index.config.ConfigInputFormat;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataFormat;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.NotFound;
import nl.inl.blacklab.server.jobs.User;
import nl.inl.blacklab.server.search.IndexManager;

/**
 * Get information about supported input formats.
 */
public class RequestHandlerListInputFormats extends RequestHandler {

	private boolean isXsltRequest;

	public RequestHandlerListInputFormats(BlackLabServer servlet, HttpServletRequest request, User user, String indexName, String urlResource, String urlPathPart) {
		super(servlet, request, user, indexName, urlResource, urlPathPart);
		isXsltRequest = urlResource != null && urlResource.length() > 0 && urlPathInfo != null && urlPathInfo.equals("xslt");
	}

	@Override
	public boolean isCacheAllowed() {
		return false; // You can create/delete formats, don't cache the list
	}

	@Override
	public DataFormat getOverrideType() {
		// Application expects this MIME type, don't disappoint
		if (isXsltRequest)
			return DataFormat.XML;
		return super.getOverrideType();
	}

	@Override
	public boolean omitBlackLabResponseRootElement() {
		return isXsltRequest;
	}


	private static class XslGenerator {
		public static String applyTemplates(String selector) {
			selector = StringUtils.stripStart(selector, ".");

			return "<xsl:apply-templates select=\"" + selector + "\"/>";
		}

		public static String beginTemplate(String match) {
			match = StringUtils.stripStart(match, ".");
			return "<xsl:template match=\"" + match + "\">";
		}

		public static final String endTemplate = "</xsl:template>";

		/*
		 * NOTE: leaves start as-is, but strips ./ if needed (//a/b will work)
		 * handles nulls
		 */
		public static String joinXpath(String a, String b) {

			// 	//a ./b 	-> //a/b
			//	a 	b 		-> a/b
			// 	a 	@annot 	-> a/@annot
			// 	/a 	b 		-> a/b
			// 	/a 	/b 		-> a/b
			// 	a 	//b 	-> a//b
			// 	/a 	//b 	-> a//b
			// 	//a .//b 	-> a//b


			// strip any leading . (since it's implicit)
			a = StringUtils.stripStart(a, ".");
			b = StringUtils.stripStart(b, ".");

			if (!StringUtils.startsWith(a, "//"))
				a = StringUtils.stripStart(a, "/");

			a = StringUtils.stripEnd(a, "./");


			// same for b
			if (!StringUtils.startsWith(b, "//"))
				b = StringUtils.stripStart(b, "./");

			b = StringUtils.stripEnd(b, "./");



			// split and explode into cartesian product...
			// a|b c -> a/c | b/c
			// because a/(b|c) is not valid xpath
			String[] asplit = StringUtils.split(a, "|");
			String[] bsplit = StringUtils.split(b, "|");
			List<String> result = new ArrayList<>();

			if (asplit != null && asplit.length > 0) {
				for (String _a : asplit) {
					if (bsplit != null && bsplit.length > 0) {
						for (String _b : bsplit) {
							if (_b.startsWith("/"))
								result.add(StringUtils.join(_a,_b));
							else
								result.add(joinIgnoreNull(new String[] {a,b}, '/'));
						}
					} else {
						result.add(_a);
					}
				}
			} else if (bsplit != null && bsplit.length > 0) {
				for (String _b : bsplit)
					result.add(_b);
			}

			String joined = StringUtils.join(result, "|");
			if (joined.isEmpty())
				return ".";

			return joined;



//			// since we cleaned the end, check the start of b to check if we need to insert a '/' inbetween a and b
//			if (StringUtils.startsWith(b, "/"))
//				return StringUtils.join(a,b);
//
//
//			String result = joinIgnoreNull(new String[] {a,b}, '/');
//			if (result.isEmpty())
//				result = "."; // don't output empty strings.
//			return result;
		}

		@SuppressWarnings("unused")
		public static String joinXpath(String...strings) {
			// accumulate over all strings
			String result = null;
			for (String s : strings)
				result = joinXpath(result, s);

			return result;
		}

		private static String joinIgnoreNull(String[] values, char separator) {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < values.length; i++) {
				if (values[i] == null || values[i].isEmpty())
					continue;

				if (sb.length() > 0)
					sb.append(separator);
				sb.append(values[i]);
			}
			return sb.toString();
		}
	}


	@Override
	public int handle(DataStream ds) throws BlsException {

	    if (urlResource != null && urlResource.length() > 0) {

	        if (!DocumentFormats.exists(urlResource))
                throw new NotFound("NOT_FOUND", "The format '" + urlResource + "' does not exist.");

	    	if (isXsltRequest)
	    		return handleXsltRequest(ds);

			File formatFile = DocumentFormats.getConfig(urlResource).getReadFromFile();
	        try (BufferedReader reader = DocumentFormats.getFormatFile(urlResource)) {
	        	if (reader == null)
	        		throw new NotFound("NOT_FOUND", "The format '" + urlResource + "' is not configuration-based, and therefore cannot be displayed.");

	        	ds	.startMap()
        		.entry("formatName", urlResource)
        		.entry("configFileType", FilenameUtils.getExtension(formatFile.getName()))
        		.entry("configFile", IOUtils.toString(reader))
        		.endMap();
        		return HTTP_OK;
	        } catch (IOException e1) {
				throw new RuntimeException(e1);
			}

	    }

		ds.startMap();
		ds.startEntry("user").startMap();
		ds.entry("loggedIn", user.isLoggedIn());
		if (user.isLoggedIn())
			ds.entry("id", user.getUserId());
		boolean canCreateIndex = user.isLoggedIn() ? indexMan.canCreateIndex(user.getUserId()) : false;
        ds.entry("canCreateIndex", canCreateIndex);
		ds.endMap().endEntry();

		// List supported input formats
	    ds.startEntry("supportedInputFormats").startMap();
        for (FormatDesc format: DocumentFormats.getSupportedFormats()) {
            String name = format.getName();
            if (IndexManager.userOwnsFormat(user.getUserId(), name)) {
                ds.startAttrEntry("format", "name", name).startMap()
                    .entry("displayName", format.getDisplayName())
                    .entry("description", format.getDescription())
                    .entry("configurationBased", format.isConfigurationBased())
	            .endMap().endAttrEntry();
            }
        }
	    ds.endMap().endEntry();
		ds.endMap();

		return HTTP_OK;
	}

	private int handleXsltRequest(DataStream ds) {
		ConfigInputFormat config = DocumentFormats.getConfig(urlResource);

		// We want an XSLT from this config.
		StringBuilder xslt = new StringBuilder();
		StringBuilder nameSpaces = new StringBuilder();
		StringBuilder excludeResultPrefixes = new StringBuilder();

		String optDefaultNameSpace = "";
		for (Entry<String, String> e: config.getNamespaces().entrySet()) {
			if (e.getKey().isEmpty())
				optDefaultNameSpace = "xpath-default-namespace=\"" + e.getValue() + "\"";
			else {
				nameSpaces.append(" xmlns:" + e.getKey() + "=\"" + e.getValue() + "\"");
				excludeResultPrefixes.append(" " + e.getKey());
			}
		}
		xslt.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
			.append("<xsl:stylesheet version=\"2.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" " + optDefaultNameSpace + nameSpaces + " exclude-result-prefixes=\"" + excludeResultPrefixes + "\">")
			.append("<xsl:output encoding=\"utf-8\" method=\"html\" omit-xml-declaration=\"yes\" />");

		List<String> wordPaths = new ArrayList<>();
		for (ConfigAnnotatedField af : config.getAnnotatedFields().values())
			wordPaths.add(af.getWordsPath());

		// swallow everything not explicitly matched.
		xslt.append("<xsl:template match='text()' priority='-10'>")
		// enable any of these to show any removed text between a pair of brackets
//			.append("<xsl:text>[</xsl:text>")
//			.append("<xsl:value-of select='.'/>")
//			.append("<xsl:text>]</xsl:text>")
			.append(XslGenerator.endTemplate);

		// transform inserted <hl> tags into spans
		// use local-name to sidestep namespaces
		xslt.append("<xsl:template match=\"*[local-name(.)='hl']\">")
			.append("<span class=\"hl\">")
			.append(XslGenerator.applyTemplates("node()")) // we don't know what level we're at, so explicitly match everything else
			.append("</span>")
			.append(XslGenerator.endTemplate);

		// and p into p...
		// use local-name to sidestep namespaces
		xslt.append("<xsl:template match=\"*[local-name(.)='p']\">")
			.append("<p>")
			.append(XslGenerator.applyTemplates("node()"))
			.append("</p>")
			.append(XslGenerator.endTemplate);

		// generate the templates for all words
		// NOTE: 'annotation' here refers to a linguistic annotation - not an xml attribute/annotation!
		for (ConfigAnnotatedField f : config.getAnnotatedFields().values()) {
			if (f.getAnnotations().isEmpty())
				continue; // No annotations for this word at all? can't display anything...

			// By default attempt to display the annotations of the word named "lemma" and "word"
			ConfigAnnotation wordAnnot = f.getAnnotations().get("word");
			ConfigAnnotation lemmaAnnot = f.getAnnotations().get("lemma");

			// Since the annotation can be named anything there is no guarantee there is a "word" annotation
			// (it's just a reasonable default guess), so just attempt to display whatever is first in the list otherwise
			if (wordAnnot == null)
				wordAnnot = f.getAnnotations().values().iterator().next();

			// Begin word template
			String wordBase = XslGenerator.joinXpath(config.getDocumentPath(), f.getWordsPath());
			xslt.append(XslGenerator.beginTemplate(wordBase))
				.append("<span class=\"word\">");

			// Extract lemma
			if (lemmaAnnot != null && lemmaAnnot != wordAnnot) {
				xslt.append("<xsl:attribute name=\"title\">")
    				.append("<xsl:value-of select='" + XslGenerator.joinXpath(lemmaAnnot.getBasePath(), lemmaAnnot.getValuePath()) + "'/>")
    				.append("</xsl:attribute>")
    				.append("<xsl:attribute name=\"data-toggle\">")
    				.append("<xsl:text>tooltip</xsl:text>")
    				.append("</xsl:attribute>");
			}

			// extract word
			xslt.append("<xsl:value-of select=\"" + XslGenerator.joinXpath(wordAnnot.getBasePath(), wordAnnot.getValuePath()) + "\"/>")
				.append("<xsl:text> </xsl:text>"); // space between words.

			// end word template
			xslt.append("</span>")
				.append(XslGenerator.endTemplate);
		}
		xslt.append("</xsl:stylesheet>");
		ds.plain(xslt.toString());
		return HTTP_OK;
	}

}