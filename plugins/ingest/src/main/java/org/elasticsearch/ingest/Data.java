/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.ingest;

import org.elasticsearch.common.Strings;

import java.util.*;

/**
 * Represents the data and meta data (like id and type) of a single document that is going to be indexed.
 */
public final class Data {

    private final String index;
    private final String type;
    private final String id;
    private final Map<String, Object> document;

    private boolean modified = false;

    public Data(String index, String type, String id, Map<String, Object> document) {
        this.index = index;
        this.type = type;
        this.id = id;
        this.document = document;
    }

    public Data(Data other) {
        this(other.index, other.type, other.id, new HashMap<>(other.document));
    }

    /**
     * Returns the value contained in the document for the provided path
     * @param path The path within the document in dot-notation
     * @param clazz The expected class of the field value
     * @return the value for the provided path if existing, null otherwise
     * @throws IllegalArgumentException if the field is present but is not of the type provided as argument.
     */
    public <T> T getPropertyValue(String path, Class<T> clazz) {
        if (path == null || path.length() == 0) {
            return null;
        }
        String[] pathElements = Strings.splitStringToArray(path, '.');
        assert pathElements.length > 0;

        Map<String, Object> innerMap = getParent(pathElements);
        if (innerMap == null) {
            return null;
        }

        String leafKey = pathElements[pathElements.length - 1];
        Object property = innerMap.get(leafKey);
        if (property == null) {
            return null;
        }
        if (clazz.isInstance(property)) {
            return clazz.cast(property);
        }
        throw new IllegalArgumentException("field [" + path + "] of type [" + property.getClass().getName() + "] cannot be cast to [" + clazz.getName() + "]");
    }

    /**
     * Checks whether the document contains a value for the provided path
     * @param path The path within the document in dot-notation
     * @return true if the document contains a value for the property, false otherwise
     */
    public boolean hasPropertyValue(String path) {
        if (path == null || path.length() == 0) {
            return false;
        }
        String[] pathElements = Strings.splitStringToArray(path, '.');
        assert pathElements.length > 0;
        Map<String, Object> innerMap = getParent(pathElements);
        if (innerMap == null) {
            return false;
        }
        String leafKey = pathElements[pathElements.length - 1];
        return innerMap.containsKey(leafKey);
    }

    /**
     * Removes the property identified by the provided path
     * @param path the path of the property to be removed
     */
    public void removeProperty(String path) {
        if (path == null || path.length() == 0) {
            return;
        }
        String[] pathElements = Strings.splitStringToArray(path, '.');
        assert pathElements.length > 0;
        Map<String, Object> parent = getParent(pathElements);
        if (parent != null) {
            String leafKey = pathElements[pathElements.length - 1];
            if (parent.containsKey(leafKey)) {
                modified = true;
                parent.remove(leafKey);
            }
        }
    }

    private Map<String, Object> getParent(String[] pathElements) {
        Map<String, Object> innerMap = document;
        for (int i = 0; i < pathElements.length - 1; i++) {
            Object obj = innerMap.get(pathElements[i]);
            if (obj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> stringObjectMap = (Map<String, Object>) obj;
                innerMap = stringObjectMap;
            } else {
                return null;
            }
        }
        return innerMap;
    }

    /**
     * Sets the provided value to the provided path in the document.
     * Any non existing path element will be created.
     * @param path The path within the document in dot-notation
     * @param value The value to put in for the path key
     */
    public void setPropertyValue(String path, Object value) {
        if (path == null || path.length() == 0) {
            throw new IllegalArgumentException("cannot add null or empty field");
        }
        String[] pathElements = Strings.splitStringToArray(path, '.');
        assert pathElements.length > 0;

        Map<String, Object> inner = document;
        for (int i = 0; i < pathElements.length - 1; i++) {
            String pathElement = pathElements[i];
            if (inner.containsKey(pathElement)) {
                Object object = inner.get(pathElement);
                if (object instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> stringObjectMap = (Map<String, Object>) object;
                    inner = stringObjectMap;
                } else if (object == null ) {
                    throw new IllegalArgumentException("cannot add field to null parent, [" + Map.class.getName() + "] expected instead.");
                } else {
                    throw new IllegalArgumentException("cannot add field to parent [" + pathElement + "] of type [" + object.getClass().getName() + "], [" + Map.class.getName() + "] expected instead.");
                }
            } else {
                Map<String, Object> newInnerMap = new HashMap<>();
                inner.put(pathElement, newInnerMap);
                inner = newInnerMap;
            }
        }

        String leafKey = pathElements[pathElements.length - 1];
        inner.put(leafKey, value);
        modified = true;
    }

    public String getIndex() {
        return index;
    }

    public String getType() {
        return type;
    }

    public String getId() {
        return id;
    }

    public Map<String, Object> getDocument() {
        return document;
    }

    public boolean isModified() {
        return modified;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) { return true; }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        Data other = (Data) obj;
        return Objects.equals(document, other.document) &&
                Objects.equals(index, other.index) &&
                Objects.equals(type, other.type) &&
                Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, type, id, document);
    }
}
