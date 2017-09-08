/*
 * Copyright (c) 2010, Marco Brade
 * 							[https://sourceforge.net/users/mbrade] All rights reserved.
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

package delight.trie.tests;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import delight.concurrency.jre.ConcurrencyJre;
import delight.trie.TrieMap;

public class TestTrieMap {

	private TrieMap<String> map;

	@Before
	public void setUp() throws Exception {
		map = new TrieMap<String>(ConcurrencyJre.create());
		map.add("/1/1", "/1/1S");
		map.add("/1/1/1", "/1/1/1S");
		map.add("/1/1/5", "/1/1/5S");
		map.add("/1/1/5/6", "/1/1/5/6S");
		map.add("/1/2", "/1/2S");
		map.add("/1/3", "/1/3S");
		map.add("/2/1", "/2/1S");
		map.add("132276", "Artikel 1");
		map.add("132277", "Artikel 2");
		map.add("132278", "Artikel 3");
		map.add("132", "Artikel 4");
	}

	@Test
	public void testAddingObjects() {
		testAddingObjects(map);
	}

	@Test
	public void testAddNothing() {
		map.add("");
		Assert.assertEquals(6, map.getSubValues("/1").size());
	}

	
	@Test
	public void testCompletitions() {
		List<String> completitions = map.getCompletitions("/1/1");
		Assert.assertEquals(4, completitions.size());
		completitions = map.getCompletitions("/1");
		Assert.assertEquals(6, completitions.size());
		completitions = map.getCompletitions("/1/1/5/6/9");
		Assert.assertEquals(0, completitions.size());
	}

	@Test
	public void testContainsKey() {
		Assert.assertTrue(map.containsKey("/1/3"));
		Assert.assertFalse(map.containsKey("/1/99"));
		Assert.assertFalse(map.containsKey(new Object()));
	}

	@Test
	public void testContainsValue() {
		Assert.assertTrue(map.containsValue("/1/1S"));
	}

	@Test
	public void testDoubleInsert() {
		Assert.assertTrue(map.add("/1/schrums"));
		Assert.assertFalse(map.add("/1/schrums"));
	}

	@Test
	public void testDoublePut() {
		map.put("someKey", "someValue");
		Assert.assertEquals("someValue", map.get("someKey"));
		Assert.assertEquals("someValue", map.put("someKey", "someOtherValue"));
		Assert.assertEquals("someOtherValue", map.get("someKey"));
	}

	@Test
	public void testEntrySet() {
		final Set<Map.Entry<String, String>> entries = map.entrySet();
		Assert.assertNotNull(entries);
		Assert.assertEquals(11, entries.size());
		Assert.assertEquals(11, map.values().size());
		for (final Map.Entry<String, String> entry : entries) {
			Assert.assertTrue(map.containsKey(entry.getKey()));
			Assert.assertTrue(map.containsValue(entry.getValue()));
			entry.setValue("A");
		}
		Assert.assertEquals(11, map.values().size());
		for (final String value : map.values()) {
			Assert.assertEquals("A", value);
		}
	}

	@Test
	public void testFindMultiObjectsOnPath() {
		final TrieMap<Long> map = new TrieMap<Long>(ConcurrencyJre.create());
		map.add("2", 99L);
		map.add("21", 98L);
		map.add("210", 97L);
		Assert.assertEquals(3, map.getValuesOnPath("210xyz").size());
	}

	@Test
	public void testFindObjectsOnPath() {
		List<String> objectsOnPath = map.getValuesOnPath("/1/1/5/6");
		Assert.assertEquals(3, objectsOnPath.size());
		Assert.assertEquals("/1/1S", objectsOnPath.get(0));
		Assert.assertEquals("/1/1/5S", objectsOnPath.get(1));
		Assert.assertEquals("/1/1/5/6S", objectsOnPath.get(2));
		objectsOnPath = map.getValuesOnPath("");
		Assert.assertEquals(0, objectsOnPath.size());
	}

	@Test
	public void testForceInsert() {
		Assert.assertTrue(map.add("/1/schrums", "schrums"));
		Assert.assertEquals("schrums", map.getSubValues("/1/schrums").get(0));
		Assert.assertFalse(map.add("/1/schrums"));
		Assert.assertFalse(map.add("/1/schrums", "schrums2"));
		Assert.assertTrue(map.forceAdd("/1/schrums", "schrums2"));
		Assert.assertEquals("schrums2", map.getSubValues("/1/schrums").get(0));
		Assert.assertTrue(map.add("/1/schrum", "schrum"));
		Assert.assertEquals("schrum", map.getSubValues("/1/schrum").get(0));
		Assert.assertEquals(2, map.getSubValues("/1/schrum").size());
	}

	@Test
	public void testGetLastMatchingObject() {
		Assert.assertEquals("/1/1S", map.getValueForBestMatchingKey("/1/1"));
	}

	@Test
	public void testGetLastMatchingObjects() {
		Assert.assertEquals("/1/1/5/6S", map.getValueForBestMatchingKey("/1/1/5/6/27"));
		Assert.assertEquals("/1/1/5/6S", map.getValueForBestMatchingKey("/1/1/5/6"));
		Assert.assertEquals("/1/1S", map.getValueForBestMatchingKey("/1/1"));
		Assert.assertNull(map.getValueForBestMatchingKey(""));
		Assert.assertNull(map.getValueForBestMatchingKey("5"));
	}

	@Test
	public void testGetPut() {
		map.put("someKey", "someValue");
		Assert.assertEquals("someValue", map.get("someKey"));
	}

	@Test
	public void testGetSubMap() {
		final TrieMap<String> subMap = map.getSubMap("/1/1/");
		Assert.assertEquals(3, subMap.size());
	}

	@Test
	public void testLastMatchingPath() {
		Assert.assertEquals("/1/1/5/6", map.getBestMatchingPath("/1/1/5/6/27"));
		Assert.assertEquals("/1/1", map.getBestMatchingPath("/1/1"));
		Assert.assertEquals("/", map.getBestMatchingPath("/"));
		Assert.assertNull(map.getBestMatchingPath(""));
		Assert.assertEquals("132", map.getBestMatchingPath("132"));
		Assert.assertNull(map.getBestMatchingPath("5"));
	}

	@Test
	public void testMatchPrefix() {
		Assert.assertTrue(map.containsPrefix("/1/1"));
		Assert.assertFalse(map.containsPrefix("/1/1/5/6/9"));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testMultiValues() {
		final TrieMap map = new TrieMap<Long>(ConcurrencyJre.create());
		map.add("1", 1L);
		map.add("1", 2L);
		map.add("1", 3L);
		map.forceAdd("1", 4L);
		map.add("2", 99L);
		Assert.assertEquals(1, map.getSubValues("1").size());
	}

	

	@Test
	public void testPutAll() {
		final Map<String, String> values = new HashMap<String, String>();
		values.put("x", "X");
		values.put("y", "Y");
		map.putAll(values);
		Assert.assertEquals("X", map.get("x"));
		Assert.assertEquals("Y", map.get("y"));
	}

	@Test
	public void testRemove() {
		Assert.assertEquals("/1/1S", map.remove("/1/1"));
		Assert.assertNull(map.remove("NOTHING"));
	}

	/**
	 * Deserialization not supported.
	 * 
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	@Ignore
	@Test
	public void testSerialization() throws IOException, ClassNotFoundException {
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		final ObjectOutputStream oos = new ObjectOutputStream(baos);
		oos.writeObject(map);
		oos.close();
		final ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()));
		final TrieMap smap = (TrieMap) ois.readObject();
		ois.close();
		Assert.assertEquals(map, smap);
		testAddingObjects(smap);
		System.out.println(baos.size());
	}

	@Test
	public void testSizeClearSize() {
		final TrieMap<String> map = new TrieMap<String>(ConcurrencyJre.create());
		for (int i = 0; i < 10; i++) {
			map.add("" + i, "" + i);
		}
		Assert.assertEquals(10, map.size());
		Assert.assertFalse(map.isEmpty());
		map.clear();
		Assert.assertTrue(map.isEmpty());
		Assert.assertEquals(0, map.size());
		for (int i = 0; i < 10; i++) {
			map.add("" + i, "" + i);
		}
		Assert.assertEquals(10, map.size());
	}

	@Test
	public void testTostring() {
		final String output = new String(
				"{/1/1 : /1/1S;\n/1/1/1 : /1/1/1S;\n/1/1/5 : /1/1/5S;\n/1/1/5/6 : /1/1/5/6S;\n/1/2 : /1/2S;\n/1/3 : /1/3S;\n/2/1 : /2/1S;\n132 : Artikel 4;\n132276 : Artikel 1;\n132277 : Artikel 2;\n132278 : Artikel 3;\n}");
		Assert.assertEquals(output, map.toString());
	}

	@Test
	public void testUseCase() {
		final TrieMap<String> map = new TrieMap<String>(ConcurrencyJre.create());
		final String NORMAL = "Normal";
		final String DEBUG = "Debug";
		map.add("de.package.tool", NORMAL);
		map.add("de.package.tool.test", DEBUG);
		Assert.assertEquals(NORMAL, map.getValueForBestMatchingKey("de.package.tool"));
		Assert.assertEquals(NORMAL, map.getValueForBestMatchingKey("de.package.tool.xyz"));
		Assert.assertEquals(DEBUG, map.getValueForBestMatchingKey("de.package.tool.test"));
		Assert.assertEquals(DEBUG, map.getValueForBestMatchingKey("de.package.tool.test.xyz"));
	}

	@Test
	public void testUseCase2() {
		final TrieMap<Long> map = new TrieMap<Long>(ConcurrencyJre.create());
		map.add("Mozilla a", 1L);
		map.add("Mozilla b", 2L);
		map.add("Mozilla ab", 3L);
		if (map.containsPrefix("Mozilla abc")) {

		} else {
			Assert.assertEquals(3L, map.getValueForBestMatchingKey("Mozilla abc").longValue());

		}
	}

	@Test
	public void testValues() {
		Assert.assertEquals(11, map.values().size());
	}

	private void testAddingObjects(final TrieMap map) {
		Assert.assertEquals(6, map.getSubValues("/1").size());
		final List<String> strings = map.getSubValues("/1");

		Assert.assertEquals("/1/1S", strings.get(0));
		Assert.assertEquals("/1/1/1S", strings.get(1));
		Assert.assertEquals("/1/1/5S", strings.get(2));
		Assert.assertEquals("/1/1/5/6S", strings.get(3));
		Assert.assertEquals("/1/2S", strings.get(4));
		Assert.assertEquals("/1/3S", strings.get(5));
		Assert.assertEquals(1, map.getSubValues("/2").size());
		Assert.assertEquals("/2/1S", map.getSubValues("/2").get(0));
		Assert.assertEquals("/2/1S", map.getSubValues("/2/1").get(0));

		Assert.assertEquals(0, map.getSubValues("/1/1/5/6/").size());

		Assert.assertFalse(map.add("/1/1/5/6"));
	}

}
