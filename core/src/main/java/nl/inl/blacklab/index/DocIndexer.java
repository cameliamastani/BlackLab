/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package nl.inl.blacklab.index;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import nl.inl.blacklab.search.indexstructure.FieldType;
import nl.inl.util.UnicodeStream;

/**
 * Indexes a file.
 */
public abstract class DocIndexer {

    protected Indexer indexer;

	/**
	 * Thrown when the maximum number of documents has been reached
	 */
	public static class MaxDocsReachedException extends RuntimeException {
		//
	}

    /**
     * Returns our Indexer object
     * @return the Indexer object
     */
    public Indexer getIndexer() {
        return indexer;
    }

    /**
     * Set the indexer object. Called by Indexer when the DocIndexer is instantiated.
     *
     * @param indexer our indexer object
     */
    public void setIndexer(Indexer indexer) {
        this.indexer = indexer;

        // Get our parameters from the indexer
        Map<String, String> indexerParameters = indexer.getIndexerParameters();
        if (indexerParameters != null)
            setParameters(indexerParameters);
    }

    /**
     * Set the document to index.
     *
     * NOTE: you should generally prefer calling the File or byte[] versions
     * of this method, as those can be more efficient (e.g. when using DocIndexer that
     * parses using VTD-XML).
     *
     * @param fileName name of the document
     * @param reader document
     */
	public abstract void setDocument(String fileName, Reader reader);

    /**
     * Set the document to index.
     *
     * @param fileName name of the document
     * @param is document contents
     * @param cs charset to use if no BOM found, or null for the default (utf-8)
     */
    public void setDocument(String fileName, InputStream is, Charset cs) {
        try {
            setDocument(fileName, new InputStreamReader(new UnicodeStream(is, cs)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Set the document to index.
     *
     * @param fileName name of the document
     * @param contents document contents
     * @param cs charset to use if no BOM found, or null for the default (utf-8)
     */
    public void setDocument(String fileName, byte[] contents, Charset cs) {
        setDocument(fileName, new ByteArrayInputStream(contents), cs);
    }

    /**
     * Set the document to index.
     *
     * @param fileName name of the document
     * @param file file to index
     * @param charset charset to use if no BOM found, or null for the default (utf-8)
     * @throws FileNotFoundException if not found
     */
    public void setDocument(String fileName, File file, Charset charset) throws FileNotFoundException {
        setDocument(fileName, new FileInputStream(file), charset);
    }

    /**
	 * Index documents contained in a file.
	 *
	 * @throws Exception
	 */
	public abstract void index() throws Exception;

    /**
     * Parameters passed to this indexer
     */
    protected Map<String, String> parameters = new HashMap<>();

    /**
     * Check if the specified parameter has a value
     * @param name parameter name
     * @return true iff the parameter has a value
     */
    public boolean hasParameter(String name) {
        return parameters.containsKey(name);
    }

    /**
     * Set a parameter for this indexer (such as which type of metadata block to process)
     * @param name parameter name
     * @param value parameter value
     */
    public void setParameter(String name, String value) {
        parameters.put(name, value);
    }

    /**
     * Set a number of parameters for this indexer
     * @param param the parameter names and values
     */
    public void setParameters(Map<String, String> param) {
        for (Map.Entry<String, String> e: param.entrySet()) {
            parameters.put(e.getKey(), e.getValue());
        }
    }

    /**
     * Get a parameter that was set for this indexer
     * @param name parameter name
     * @param defaultValue parameter default value
     * @return the parameter value (or the default value if it was not specified)
     */
    public String getParameter(String name, String defaultValue) {
        String value = parameters.get(name);
        if (value == null)
            return defaultValue;
        return value;
    }

    /**
     * Get a parameter that was set for this indexer
     * @param name parameter name
     * @return the parameter value (or null if it was not specified)
     */
    public String getParameter(String name) {
        return getParameter(name, null);
    }

    /**
     * Get a parameter that was set for this indexer
     * @param name parameter name
     * @param defaultValue parameter default value
     * @return the parameter value (or the default value if it was not specified)
     */
    public boolean getParameter(String name, boolean defaultValue) {
        String value = parameters.get(name);
        if (value == null)
            return defaultValue;
        value = value.trim().toLowerCase();
        return value.equals("true") || value.equals("1") || value.equals("yes");
    }

    /**
     * Get a parameter that was set for this indexer
     * @param name parameter name
     * @param defaultValue parameter default value
     * @return the parameter value (or the default value if it was not specified)
     */
    public int getParameter(String name, int defaultValue) {
        String value = parameters.get(name);
        if (value == null)
            return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

	/**
	 * Return the fieldtype to use for the specified field.
	 * @param fieldName the field name
	 * @return the fieldtype
	 */
	public abstract FieldType getMetadataFieldTypeFromIndexerProperties(String fieldName);
}
