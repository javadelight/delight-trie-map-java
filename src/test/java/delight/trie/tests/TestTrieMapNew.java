package delight.trie.tests;

import org.junit.Assert;
import org.junit.Test;

import delight.concurrency.jre.ConcurrencyJre;
import delight.trie.TrieMap;

public class TestTrieMapNew {

	@Test
	public void testRanges() throws Exception {
		
		TrieMap<String> map = new TrieMap<String>(ConcurrencyJre.create());
		
		
		map.put("test66/A1/B1/C1", "C1");
		map.put("test66/A1/B1/C1/D1/E1/F1/", "F1");
		
		Assert.assertEquals("test66/A1/B1/C1/D1/E1/", map.getBestMatchingPath("test66/A1/B1/C1/D1/E1/"));
		
		Assert.assertEquals("test66/A1/B1/C1/D1/E1/F1/", map.getBestMatchingPath("test66/A1/B1/C1/D1/E1/F1/G1"));
		
		Assert.assertTrue(map.getSubMap("test66/A1/B1/C1/D1/E1/").containsKey("test66/A1/B1/C1/D1/E1/F1/"));
		
		Assert.assertEquals(1, map.getValuesOnPath("test66/A1/B1/C1/D1/E1/").size()); // [C1]
		
		Assert.assertEquals(2, map.getValuesOnPath("test66/A1/B1/C1/D1/E1/F1/").size()); // [C1, F1]

		
		
		
	}
	
}
