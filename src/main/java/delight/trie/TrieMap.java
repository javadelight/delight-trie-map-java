package delight.trie;

/*
 * Copyright (c) 2010, Marco Brade [https://sourceforge.net/users/mbrade] All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright notice,
 *       this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright notice,
 *       this list of conditions and the following disclaimer in the documentation
 *       and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import delight.concurrency.Concurrency;
import delight.concurrency.wrappers.SimpleReadWriteLock;

/**
 * The TrieMap stores a list of strings in a tree based way.<br/>
 * On each String it is possible to assign an object.<br/>
 * Each Key-String can represent only one object.
 * 
 * @param <Value>
 *            the value type
 * @author Marco Brade
 */
public class TrieMap<Value> implements Serializable, Map<String, Value>, Cloneable {

	/** The Constant serialVersionUID. */
	private static final long serialVersionUID = 1L;

	private transient final Concurrency concurrency;
	
	/** The root node. */
	private transient TrieNode<Value> rootNode;

	private transient SimpleReadWriteLock lock; //= new SimpleReadWriteLock();

	/**
	 * Instantiates a new trie map.
	 */
	public TrieMap(Concurrency conn) {
		concurrency = conn;
		lock = conn.newReadWriteLock();
		rootNode = new TrieNode<Value>(' ', null, false);
	}

	private static boolean isEmptyStr(final CharSequence test) {
		return test == null || test.length() == 0;
	}

	/**
	 * Instantiates a new trie map.
	 * 
	 * @param map
	 *            the map
	 */
	public TrieMap(Concurrency conn, final Map<String, Value> map) {
		this(conn);
		putAll(map);
	}

	/**
	 * Adds the phrase to the TrieMap<br/>
	 * If the phrase has been added before and multivalue is disabled it will
	 * return false.<br/>
	 * 
	 * @param phrase
	 *            the phrase
	 * @return true, if successful
	 */
	public boolean add(final String phrase) {
		return addRecursive(rootNode, phrase, null, false);
	}

	/**
	 * Adds the phrase to the TrieMap and assigns the given object to it.<br/>
	 * If the phrase has been added before it will return false. The previous
	 * stored object will stay as is even if it has not been set.<br/>
	 * 
	 * @param phrase
	 *            the phrase
	 * @param object
	 *            the object
	 * @return true, if successful
	 */
	public boolean add(final String phrase, final Value object) {
		return addRecursive(rootNode, phrase, object, false);
	}

	/**
	 * As properties.
	 * 
	 * @return the properties
	 */
	public Properties asProperties() {
		return new TrieMapBackedProperties();
	}

	/**
	 * As properties.
	 * 
	 * @param defaults
	 *            the defaults
	 * @return the properties
	 */
	public Properties asProperties(final Properties defaults) {
		return new TrieMapBackedProperties(defaults);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#clear()
	 */
	@Override
	public void clear() {
		rootNode = new TrieNode<Value>(' ', null, false);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#clone()
	 */
	@SuppressWarnings("unchecked")
	@Override
	public TrieMap<Value> clone() {
		TrieMap<Value> clone = null;
		try {
			clone = (TrieMap<Value>) super.clone();
			clone.rootNode = rootNode.clone();
		} catch (final CloneNotSupportedException e) {
		}
		return clone;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#containsKey(java.lang.Object)
	 */
	@Override
	public boolean containsKey(final Object key) {
		if (key instanceof String) {
			return containsPrefix((String) key);
		}
		return false;
	}

	/**
	 * Checks if a node would match the prefix.
	 * 
	 * @param prefix
	 *            the prefix
	 * 
	 * @return true, if successful
	 */
	public boolean containsPrefix(final String prefix) {
		final TrieNode<Value> matchedNode = matchPrefixRecursive(rootNode, prefix);
		return (matchedNode != null);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#containsValue(java.lang.Object)
	 */
	@Override
	public boolean containsValue(final Object value) {
		return getPathForValue(value) != null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#entrySet()
	 */
	@Override
	public Set<java.util.Map.Entry<String, Value>> entrySet() {
		final Set<Map.Entry<String, Value>> result = new TreeSet<Map.Entry<String, Value>>();
		for (final String key : keySet()) {
			result.add(new Entry(key));
		}
		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		@SuppressWarnings("rawtypes")
		final TrieMap other = (TrieMap) obj;
		if (rootNode == null) {
			if (other.rootNode != null) {
				return false;
			}
		} else if (!rootNode.equals(other.rootNode)) {
			return false;
		}
		return true;
	}

	/**
	 * Forces to add the phrase and assigns the given object to it even if an
	 * existing object has been set before.<br/>
	 * 
	 * @param phrase
	 *            the phrase
	 * @param object
	 *            the object
	 * @return true, if successful
	 */
	public boolean forceAdd(final String phrase, final Value object) {
		return addRecursive(rootNode, phrase, object, true);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#get(java.lang.Object)
	 */
	@Override
	public Value get(final Object key) {
		if (key instanceof String) {
			final TrieNode<Value> matchedNode = matchPrefixRecursive(rootNode, (String) key);
			if (matchedNode != null) {
				final Value result = matchedNode.getObject();
				return result;
			}
		}
		return null;
	}

	/**
	 * Finds the path that is best matching the given prefix.<br/>
	 * Returns null if the given prefix is empty or the given prefix is not
	 * contained in this TrieMap.<br/>
	 * The returned path might be a part of the prefix, the complete prefix or
	 * null if the no part of the prefix is contained in this TrieMap<br/>
	 * The result might not be a complete key.
	 * 
	 * @param prefix
	 *            the prefix
	 * @return the best matching prefix
	 */
	public String getBestMatchingPath(final String prefix) {
		if (isEmpty(prefix)) {
			return null;
		}
		final TrieNode<Value> trieNode = rootNode.getChildNode(prefix.charAt(0));
		if (trieNode != null) {
			final StringBuilder builder = new StringBuilder().append(prefix.charAt(0));
			final String subPrefix = prefix.substring(1);
			if (isEmptyStr(subPrefix)) {
				return builder.toString();
			} else {
				return findLastMatchingRecursivly(trieNode, prefix.substring(1), builder);
			}
		} else {
			return null;
		}
	}

	/**
	 * Returns the Strings stored below the given prefix.
	 * 
	 * @param prefix
	 *            the prefix
	 * @return the list
	 */
	public List<String> getCompletitions(final String prefix) {
		final TrieNode<Value> matchedNode = matchPrefixRecursive(rootNode, prefix);
		final List<String> completions = new ArrayList<String>();
		findCompletionsRecursive(matchedNode, prefix, completions);
		return completions;
	}

	/**
	 * Gets the path for the given value.
	 * 
	 * @param objectToFind
	 *            the object to find
	 * @return the path for object
	 */
	public String getPathForValue(final Object objectToFind) {
		final String path = findPathForObject(rootNode, "", objectToFind);
		return path;
	}

	/**
	 * Gets a Map of Objects with it's keys that are below the given prefix.
	 * 
	 * @param prefix
	 *            the prefix
	 * @return the object entries
	 */
	public TrieMap<Value> getSubMap(final String prefix) {
		final TrieNode<Value> matchedNode = matchPrefixRecursive(rootNode, prefix);
		final TrieMap<Value> completitions = new TrieMap<Value>(concurrency);
		findObjectMapRecursive(matchedNode, prefix, completitions);
		return completitions;
	}

	/**
	 * Returns the stored object below the given prefix. Or an empty list if no
	 * objects have been stored.
	 * 
	 * @param prefix
	 *            the prefix
	 * @return the list
	 */
	public List<Value> getSubValues(final String prefix) {
		final TrieNode<Value> matchedNode = (prefix == null) ? rootNode : matchPrefixRecursive(rootNode, prefix);
		final List<Value> completions = new LinkedList<Value>();
		findObjectsRecursive(matchedNode, prefix, completions);
		return completions;
	}

	/**
	 * Gets the last found object of the best matching path for the given
	 * prefix<br/>
	 * Will return null if the given prefix is empty.
	 * 
	 * @param prefix
	 *            the prefix
	 * @return the last matching objects
	 */
	public Value getValueForBestMatchingKey(final String prefix) {
		if (isEmpty(prefix)) {
			return null;
		}
		final Value result = getLastMatchingObject(rootNode, prefix, null);
		return result;
	}

	/**
	 * Gets the objects that lie on the given path. The path has to be
	 * complete.<br/>
	 * It means it has to be a value which has been added by the
	 * {@link #add(String, Object)} method.
	 * 
	 * @param <T>
	 *            the generic type
	 * @param prefix
	 *            the prefix
	 * @return the objects on path
	 */
	public <T extends Value> List<Value> getValuesOnPath(final String prefix) {
		if (isEmpty(prefix)) {
			return Collections.emptyList();
		}
		final List<TrieNode<Value>> matchedNodes = matchNodesOnPathRecursive(rootNode, prefix);
		final List<Value> result = new ArrayList<Value>(matchedNodes.size());
		for (final TrieNode<Value> node : matchedNodes) {
			if (node.containsObject()) {
				final Value object = node.getObject();
				if (object != null) {
					result.add(object);
				}
			}
		}
		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((rootNode == null) ? 0 : rootNode.hashCode());
		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#isEmpty()
	 */
	@Override
	public boolean isEmpty() {
		return rootNode.getChildren().isEmpty();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#keySet()
	 */
	@Override
	public Set<String> keySet() {
		final List<String> keys = getCompletitions("");
		keys.remove("" + rootNode.getNodeValue());
		return new TreeSet<String>(keys);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#put(java.lang.Object, java.lang.Object)
	 */
	@Override
	public Value put(final String key, final Value value) {
		final Value preResult = get(key);
		forceAdd(key, value);
		return preResult;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#putAll(java.util.Map)
	 */
	@Override
	public void putAll(final Map<? extends String, ? extends Value> m) {
		if (m != null) {
			for (final Map.Entry<? extends String, ? extends Value> entry : m.entrySet()) {
				forceAdd(entry.getKey(), entry.getValue());
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#remove(java.lang.Object)
	 */
	@Override
	public Value remove(final Object key) {
		if (key instanceof String) {
			final TrieNode<Value> matchedNode = matchPrefixRecursive(rootNode, (String) key);
			if (matchedNode != null) {
				final Value object = matchedNode.removeObject();
				matchedNode.setBoundary(false);
				return object;
			}
		}
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#size()
	 */
	@Override
	public int size() {
		return keySet().size();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return rootNode.toString();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Map#values()
	 */
	@Override
	public Collection<Value> values() {
		return getSubValues("");
	}

	private boolean addRecursive(final TrieNode<Value> node, final String phrase, final Value object,
			final boolean force) {
		if (isEmpty(phrase)) {
			return true;
		}
		final char firstChar = phrase.charAt(0);

		final String subString = phrase.substring(1);
		if (isEmpty(subString)) {
			if (node.add(firstChar, object, force, true)) {
				return true;
			}
			return false;
		} else {
			node.add(firstChar, null, force, false);
		}

		final TrieNode<Value> childNode = node.getChildNode(firstChar);
		if (childNode != null) {
			return addRecursive(childNode, subString, object, force);
		}
		return false;
	}

	private void findCompletionsRecursive(final TrieNode<Value> node, final String prefix,
			final List<String> completions) {
		if (node == null) {
			return;
		}
		if (node.isBoundary()) {
			completions.add(prefix);
		}
		final Collection<TrieNode<Value>> childNodes = node.getChildren();
		for (final TrieNode<Value> childNode : childNodes) {
			final char childChar = childNode.getNodeValue();
			findCompletionsRecursive(childNode, prefix + childChar, completions);
		}
	}

	private String findLastMatchingRecursivly(final TrieNode<Value> node, final String phrase,
			final StringBuilder completion) {
		final char firstChar = phrase.charAt(0);
		final String subString = phrase.substring(1);
		if (isEmpty(subString)) {
			completion.append(firstChar);
			return completion.toString();
		}

		final TrieNode<Value> childNode = node.getChildNode(firstChar);
		if (childNode != null) {
			completion.append(firstChar);
			return findLastMatchingRecursivly(childNode, subString, completion);
		} else {
			return completion.toString();
		}
	}

	private void findObjectMapRecursive(final TrieNode<Value> node, final String prefix,
			final TrieMap<Value> completions) {
		if (node == null) {
			// our prefix did not match anything we return
			return;
		}
		if (node.containsObject()) {
			final Value object = node.getObject();
			if (object != null) {
				completions.put(prefix, object);
			}
		}
		final Collection<TrieNode<Value>> childNodes = node.getChildren();
		for (final TrieNode<Value> childNode : childNodes) {
			final char childChar = childNode.getNodeValue();
			findObjectMapRecursive(childNode, prefix + childChar, completions);
		}
	}

	private void findObjectsRecursive(final TrieNode<Value> node, final String prefix, final List<Value> completions) {
		if (node == null) {
			// our prefix did not match anything we return
			return;
		}
		if (node.containsObject()) {
			final Value object = node.getObject();
			if (object != null) {
				completions.add(object);
			}
		}
		final Collection<TrieNode<Value>> childNodes = node.getChildren();
		for (final TrieNode<Value> childNode : childNodes) {
			final char childChar = childNode.getNodeValue();
			findObjectsRecursive(childNode, prefix + childChar, completions);
		}
	}

	private String findPathForObject(final TrieNode<Value> node, final String prefix, final Object toFind) {
		if (node == null) {
			// our prefix did not match anything we return
			return null;
		}
		if (node.containsObject()) {
			final Value object = node.getObject();
			if (object != null && object.equals(toFind)) {
				return prefix;
			}
		}
		final Collection<TrieNode<Value>> childNodes = node.getChildren();
		for (final TrieNode<Value> childNode : childNodes) {
			final char childChar = childNode.getNodeValue();
			final String path = findPathForObject(childNode, prefix + childChar, toFind);
			if (path != null) {
				return path;
			}
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private Value getLastMatchingObject(final TrieNode node, final String prefix, final Value lastObject) {
		if (isEmpty(prefix)) {
			return lastObject;
		}
		final char firstChar = prefix.charAt(0);
		final TrieNode childNode = node.getChildNode(firstChar);
		if (childNode == null) {
			// no match at this char, exit
			return lastObject;
		} else {
			// go deeper
			return getLastMatchingObject(childNode, prefix.substring(1),
					((Value) ((childNode.containsObject()) ? childNode.getObject() : lastObject)));
		}
	}

	private boolean isEmpty(final String phrase) {
		return isEmptyStr(phrase);
	}

	private List<TrieNode<Value>> matchNodesOnPathRecursive(final TrieNode<Value> node, final String prefix) {
		return matchNodesOnPathRecursive(node, prefix, new LinkedList<TrieNode<Value>>());
	}

	private List<TrieNode<Value>> matchNodesOnPathRecursive(final TrieNode<Value> node, final String prefix,
			final List<TrieNode<Value>> result) {
		if (isEmpty(prefix)) {
			result.add(node);
			return result;
		}
		final char firstChar = prefix.charAt(0);
		final TrieNode<Value> childNode = node.getChildNode(firstChar);
		if (childNode == null) {
			// no match at this char, exit
			if (node.isBoundary()) {
				result.add(node);
			}
			return result;
		} else {
			// go deeper
			if (node.isBoundary()) {
				result.add(node);
			}
			return matchNodesOnPathRecursive(childNode, prefix.substring(1), result);
		}
	}

	private TrieNode<Value> matchPrefixRecursive(final TrieNode<Value> node, final String prefix) {
		if (isEmpty(prefix)) {
			return node;
		}
		final char firstChar = prefix.charAt(0);
		final TrieNode<Value> childNode = node.getChildNode(firstChar);
		if (childNode == null) {
			// no match at this char, exit
			return null;
		} else {
			// go deeper
			return matchPrefixRecursive(childNode, prefix.substring(1));
		}
	}

	@SuppressWarnings("unchecked")
	private void readObject(final ObjectInputStream in) throws IOException, ClassNotFoundException {
		lock = concurrency.newReadWriteLock();
		// rootNode = (TrieNode<Value>) in.readObject();
		rootNode = new TrieNode<Value>(' ', null, false);
		final int size = in.readInt();
		String key;
		Value value;
		for (int i = 0; i < size; i++) {
			key = (String) in.readObject();
			value = (Value) in.readObject();
			add(key, value);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.io.Externalizable#writeExternal(java.io.ObjectOutput)
	 */
	private void writeObject(final ObjectOutputStream out) throws IOException {
		try {
			lock.readLock().lock();
			final Set<Map.Entry<String, Value>> entrySet = entrySet();
			out.writeInt(entrySet.size());
			for (final Map.Entry<String, Value> entry : entrySet) {
				out.writeObject(entry.getKey());
				out.writeObject(entry.getValue());
			}
		} finally {
			lock.readLock().unlock();
		}
	}

	/**
	 * Gets the key set.
	 * 
	 * @return the key set
	 */
	@SuppressWarnings("unchecked")
	protected Set<Object> getkeySet() {
		final List keys = getCompletitions("");
		keys.remove("" + rootNode.getNodeValue());
		return new TreeSet<Object>(keys);
	}

	/**
	 * Gets the object values.
	 * 
	 * @param prefix
	 *            the prefix
	 * @return the object values
	 */
	@SuppressWarnings("unchecked")
	protected List<Object> getObjectValues(final String prefix) {
		final List<Object> completions = new ArrayList(getSubValues(prefix));
		return completions;
	}

	/**
	 * Internal entry set.
	 * 
	 * @return the sets the
	 */
	@SuppressWarnings("unchecked")
	protected Set internalEntrySet() {
		final Set result = new HashSet();
		for (final String key : keySet()) {
			result.add(new Entry(key));
		}
		return result;
	}

	/**
	 * java.util.Properties extension backed by this given TrieMap.
	 * 
	 * @author Marco Brade
	 * 
	 */
	public final class TrieMapBackedProperties extends Properties implements Cloneable {

		/** The Constant serialVersionUID. */
		private static final long serialVersionUID = 1L;

		/** The none string key map. */
		private final Map<Object, Object> noneStringKeyMap = new HashMap<Object, Object>();

		/** The lock. */
		private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

		private TrieMapBackedProperties() {
		}

		private TrieMapBackedProperties(final Properties defaults) {
			super(defaults);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.util.Hashtable#clear()
		 */
		@Override
		public void clear() {
			try {
				lock.writeLock().lock();
				TrieMap.this.clear();
				noneStringKeyMap.clear();
			} finally {
				lock.writeLock().unlock();
			}
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.util.Hashtable#clone()
		 */
		@Override
		public TrieMapBackedProperties clone() {
			try {
				lock.readLock().lock();
				final TrieMap<Value> clonedMap = TrieMap.this.clone();
				final TrieMapBackedProperties clone = (TrieMapBackedProperties) clonedMap.asProperties(defaults);
				clone.noneStringKeyMap.putAll(noneStringKeyMap);
				return clone;
			} finally {
				lock.readLock().unlock();
			}

		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.util.Hashtable#contains(java.lang.Object)
		 */
		@Override
		public boolean contains(final Object value) {
			return containsValue(value);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.util.Hashtable#containsKey(java.lang.Object)
		 */
		@Override
		public boolean containsKey(final Object key) {
			try {
				lock.readLock().lock();
				return TrieMap.this.containsKey(key) || noneStringKeyMap.containsKey(key);
			} finally {
				lock.readLock().unlock();
			}
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.util.Hashtable#containsValue(java.lang.Object)
		 */
		@Override
		public boolean containsValue(final Object value) {
			if (value == null) {
				throw new NullPointerException();
			}
			try {
				lock.readLock().lock();
				return TrieMap.this.containsValue(value) || noneStringKeyMap.containsValue(value);
			} finally {
				lock.readLock().unlock();
			}
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.util.Hashtable#elements()
		 */
		@Override
		public Enumeration<Object> elements() {
			final Iterator<Object> iterator = values().iterator();
			return new Enumeration<Object>() {

				@Override
				public boolean hasMoreElements() {
					return iterator.hasNext();
				}

				@Override
				public Object nextElement() {
					return iterator.next();
				}
			};
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.util.Hashtable#entrySet()
		 */
		@SuppressWarnings("unchecked")
		@Override
		public Set<Map.Entry<Object, Object>> entrySet() {
			try {
				lock.readLock().lock();
				final Set entrySet = TrieMap.this.internalEntrySet();
				entrySet.addAll(noneStringKeyMap.entrySet());
				return entrySet;
			} finally {
				lock.readLock().unlock();
			}
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.util.Hashtable#equals(java.lang.Object)
		 */
		@Override
		public boolean equals(final Object obj) {
			try {
				lock.readLock().lock();
				if (this == obj) {
					return true;
				}
				if (!super.equals(obj)) {
					return false;
				}
				if (getClass() != obj.getClass()) {
					return false;
				}
				final TrieMapBackedProperties other = (TrieMapBackedProperties) obj;
				if (!getOuterType().equals(other.getOuterType())) {
					return false;
				}
				if (noneStringKeyMap == null) {
					if (other.noneStringKeyMap != null) {
						return false;
					}
				} else if (!noneStringKeyMap.equals(other.noneStringKeyMap)) {
					return false;
				}
				return true;
			} finally {
				lock.readLock().unlock();
			}
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.util.Hashtable#get(java.lang.Object)
		 */
		@Override
		public Object get(final Object key) {
			try {
				lock.readLock().lock();
				Object result = TrieMap.this.get(key);
				if (result == null) {
					result = noneStringKeyMap.get(key);
				}
				return result;
			} finally {
				lock.readLock().unlock();
			}
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.util.Properties#getProperty(java.lang.String)
		 */
		@Override
		public String getProperty(final String key) {
			final Object oval = get(key);
			final String sval = (oval instanceof String) ? (String) oval : null;
			return ((sval == null) && (defaults != null)) ? defaults.getProperty(key) : sval;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.util.Hashtable#hashCode()
		 */
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 0;
			result = prime * result + getOuterType().hashCode();
			result = prime * result + ((noneStringKeyMap == null) ? 0 : noneStringKeyMap.hashCode());
			return result;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.util.Hashtable#isEmpty()
		 */
		@Override
		public boolean isEmpty() {
			try {
				lock.readLock().lock();
				return TrieMap.this.isEmpty() && noneStringKeyMap.isEmpty();
			} finally {
				lock.readLock().unlock();
			}
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.util.Hashtable#keys()
		 */
		@Override
		public Enumeration<Object> keys() {
			final Iterator<Object> it = keySet().iterator();
			return new Enumeration<Object>() {

				@Override
				public boolean hasMoreElements() {
					return it.hasNext();
				}

				@Override
				public Object nextElement() {
					return it.next();
				}
			};
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.util.Hashtable#keySet()
		 */
		@Override
		public Set<Object> keySet() {
			try {
				lock.readLock().lock();
				final Set<Object> keys = new HashSet<Object>(TrieMap.this.getkeySet());
				keys.addAll(noneStringKeyMap.keySet());
				if (defaults != null) {
					keys.addAll(defaults.keySet());
				}
				return keys;
			} finally {
				lock.readLock().unlock();
			}
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.util.Hashtable#put(java.lang.Object, java.lang.Object)
		 */
		@SuppressWarnings("unchecked")
		@Override
		public Object put(final Object key, final Object value) {
			try {
				lock.writeLock().lock();
				if (key instanceof String) {
					return TrieMap.this.put((String) key, (Value) value);
				} else {
					return noneStringKeyMap.put(key, value);
				}
			} finally {
				lock.writeLock().unlock();
			}
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.util.Hashtable#putAll(java.util.Map)
		 */
		@SuppressWarnings("unchecked")
		@Override
		public void putAll(final Map<? extends Object, ? extends Object> t) {
			try {
				lock.writeLock().lock();
				for (final Map.Entry entry : t.entrySet()) {
					put(entry.getKey(), entry.getValue());
				}
			} finally {
				lock.writeLock().unlock();
			}
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.util.Hashtable#remove(java.lang.Object)
		 */
		@Override
		public Object remove(final Object key) {
			try {
				lock.writeLock().lock();
				Object result = TrieMap.this.remove(key);
				if (result == null) {
					result = noneStringKeyMap.remove(key);
				}
				return result;
			} finally {
				lock.writeLock().unlock();
			}
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.util.Properties#setProperty(java.lang.String,
		 * java.lang.String)
		 */
		@Override
		public Object setProperty(final String key, final String value) {
			return put(key, value);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.util.Hashtable#size()
		 */
		@Override
		public int size() {
			return TrieMap.this.size() + noneStringKeyMap.size();
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.util.Hashtable#toString()
		 */
		@Override
		public String toString() {
			return new StringBuilder(TrieMap.this.toString()).append(",").append(noneStringKeyMap.toString())
					.toString();
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.util.Hashtable#values()
		 */
		@Override
		public Collection<Object> values() {
			try {
				lock.readLock().lock();
				final List<Object> result = TrieMap.this.getObjectValues("");
				result.addAll(noneStringKeyMap.values());
				if (defaults != null) {
					result.addAll(defaults.values());
				}
				return result;
			} finally {
				lock.readLock().unlock();
			}
		}

		/**
		 * Gets the outer type.
		 * 
		 * @return the outer type
		 */
		@SuppressWarnings("unchecked")
		private TrieMap getOuterType() {
			return TrieMap.this;
		}
	}

	/**
	 * The Class Entry.
	 */
	private final class Entry implements Map.Entry<String, Value>, Comparable<Entry> {

		/** The key. */
		private final String key;

		/**
		 * Instantiates a new entry.
		 * 
		 * @param keyParam
		 *            the key param
		 */
		private Entry(final String keyParam) {
			this.key = keyParam;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Comparable#compareTo(java.lang.Object)
		 */
		@Override
		public int compareTo(final Entry o) {
			return key.compareTo(o.getKey());
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.util.Map.Entry#getKey()
		 */
		@Override
		public String getKey() {
			return key;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.util.Map.Entry#getValue()
		 */
		@Override
		public Value getValue() {
			return get(key);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.util.Map.Entry#setValue(java.lang.Object)
		 */
		@Override
		public Value setValue(final Value value) {
			final Value result = get(key);
			if (value == null) {
				remove(key);
			} else {
				forceAdd(key, value);
			}
			return result;
		}
	}

	private final class TrieNode<ValueNode> implements Cloneable {

		/** The Constant serialVersionUID. */
		private static final long serialVersionUID = 1L;

		/** The object. */
		private ValueNode object;

		/** The character. */
		private final Character character;

		/** The children. */
		private Map<Character, TrieNode<ValueNode>> children;

		/** The boundary. */
		private boolean boundary = false;

		private TrieNode(final char c, final ValueNode value, final boolean boundaryParam) {
			this.character = Character.valueOf(c);
			this.boundary = boundaryParam;
			children = new TreeMap<Character, TrieNode<ValueNode>>();
			if (value != null) {
				setValue(value);
			}
		}

		public boolean add(final char c, final ValueNode object, final boolean force, final boolean isBoundary) {
			try {
				lock.writeLock().lock();
				final TrieNode<ValueNode> node = children.get(Character.valueOf(c));
				if (node == null) {
					// children does not contain c, add a TrieNode
					children.put(Character.valueOf(c), new TrieNode<ValueNode>(c, object, isBoundary));
					return true;
				} else if (object != null && (force || !node.isBoundary())) {
					node.setValue(object);
					node.setBoundary(isBoundary);
					return true;
				}
			} finally {
				lock.writeLock().unlock();
			}
			return false;
		}

		@SuppressWarnings("unchecked")
		@Override
		public TrieNode<ValueNode> clone() {
			try {
				lock.readLock().lock();
				TrieNode<ValueNode> node = null;
				try {
					node = (TrieNode<ValueNode>) super.clone();
					final Map<Character, TrieNode<ValueNode>> childs = new TreeMap<Character, TrieNode<ValueNode>>();
					for (final TrieNode<ValueNode> nodeTrieNode : children.values()) {
						childs.put(nodeTrieNode.character, nodeTrieNode.clone());
					}
					node.children = childs;
				} catch (final CloneNotSupportedException cnse) {
				}
				return node;
			} finally {
				lock.readLock().unlock();
			}
		}

		/**
		 * Contains objects.
		 * 
		 * @return true, if successful
		 */
		public boolean containsObject() {
			try {
				lock.readLock().lock();
				return isBoundary() && object != null;
			} finally {
				lock.readLock().unlock();
			}
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		@SuppressWarnings("unchecked")
		@Override
		public boolean equals(final Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			final TrieNode<ValueNode> other = (TrieNode<ValueNode>) obj;
			if (boundary != other.boundary) {
				return false;
			}
			if (character == null) {
				if (other.character != null) {
					return false;
				}
			} else if (!character.equals(other.character)) {
				return false;
			}
			if (children == null) {
				if (other.children != null) {
					return false;
				}
			} else if (!children.equals(other.children)) {
				return false;
			}
			if (object == null) {
				if (other.object != null) {
					return false;
				}
			} else if (!object.equals(other.object)) {
				return false;
			}
			return true;
		}

		/**
		 * Gets the child node.
		 * 
		 * @param c
		 *            the c
		 * 
		 * @return the child node
		 */
		public TrieNode<ValueNode> getChildNode(final char c) {
			return children.get(Character.valueOf(c));
		}

		/**
		 * Gets the children.
		 * 
		 * @return the children
		 */
		public Collection<TrieNode<ValueNode>> getChildren() {
			try {
				lock.readLock().lock();
				return (children == null) ? Collections.<TrieNode<ValueNode>>emptyList() : children.values();
			} finally {
				lock.readLock().unlock();
			}
		}

		/**
		 * Gets the node value.
		 * 
		 * @return the node value
		 */
		public char getNodeValue() {
			return character.charValue();
		}

		/**
		 * Gets the object.
		 * 
		 * @return the object
		 */
		public ValueNode getObject() {
			try {
				lock.readLock().lock();
				return object;
			} finally {
				lock.readLock().unlock();
			}
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Object#hashCode()
		 */
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + (boundary ? 1231 : 1237);
			result = prime * result + ((character == null) ? 0 : character.hashCode());
			result = prime * result + ((children == null) ? 0 : children.hashCode());
			result = prime * result + ((object == null) ? 0 : object.hashCode());
			return result;
		}

		/**
		 * Checks if is boundary.
		 * 
		 * @return true, if is boundary
		 */
		public boolean isBoundary() {
			return boundary;
		}

		/**
		 * Removes the objects.
		 * 
		 * @return the list
		 */
		public ValueNode removeObject() {
			try {
				lock.writeLock().lock();
				// children does not contain c, add a TrieNode
				final ValueNode result = getObject();
				object = null;
				return result;
			} finally {
				lock.writeLock().unlock();
			}
		}

		/**
		 * Sets the boundary.
		 * 
		 * @param boundary
		 *            the new boundary
		 */
		public void setBoundary(final boolean boundary) {
			try {
				lock.writeLock().lock();
				this.boundary = boundary;
			} finally {
				lock.writeLock().unlock();
			}
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			final StringBuilder sb = new StringBuilder("{");
			for (final TrieNode<ValueNode> node : children.values()) {
				node.toString(sb, "");
			}
			return sb.append("}").toString();
		}

		/*
		 * @SuppressWarnings("unchecked") private void readObject(final
		 * ObjectInputStream in) throws IOException, ClassNotFoundException {
		 * in.defaultReadObject(); character = Character.valueOf(in.readChar());
		 * final byte b = in.readByte(); boundary = ((b & 1) == 1); if ((b & 2)
		 * != 0) { object = (ValueNode) in.readObject(); } final char count =
		 * in.readChar(); children = new TreeMap<Character,
		 * TrieNode<ValueNode>>(); for (char i = 0; i < count; i++) { final
		 * TrieNode<ValueNode> node = (TrieNode<ValueNode>) in.readObject();
		 * children.put(node.character, node); } }
		 */
		private Object setValue(final ValueNode obj) {
			final Object result = this.object;
			this.object = obj;
			return result;
		}

		/*
		 * private void writeObject(final ObjectOutputStream out) throws
		 * IOException { try { lock.readLock().lock(); out.defaultWriteObject();
		 * out.writeChar(character.charValue()); byte b = (boundary) ? (byte) 1
		 * : 0; b |= (object != null) ? 1 << 1 : 0; out.writeByte(b); if (object
		 * != null) { out.writeObject(object); } out.writeChar(children != null
		 * ? (char) children.size() : 0); if (children != null) { for (final
		 * TrieNode<ValueNode> node : children.values()) {
		 * out.writeObject(node); } } } finally { lock.readLock().unlock(); } }
		 */

		/**
		 * Append.
		 * 
		 * @param sb
		 *            the sb
		 * @param prefix
		 *            the prefix
		 * @return the string builder
		 */
		protected StringBuilder toString(final StringBuilder sb, final String prefix) {
			if (isBoundary() && object != null) {
				sb.append(prefix + character);
				sb.append(" : ").append(object.toString()).append(";\n");
			}
			for (final TrieNode<ValueNode> node : children.values()) {
				node.toString(sb, prefix + character.charValue());
			}
			return sb;
		}

	}

}
