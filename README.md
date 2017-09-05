# Trie Map (Java Delight)

An implementation of the Trie Map data structure in Java - for efficient search for strings starting with a value.

This project is a fork of the [triemap](https://sourceforge.net/projects/triemap/) project by Marco Brade. Initially this version just removes the 
dependency to Apache Commons but future updates might include further changed. 

## Usage

Use it just like a normal Java Map for adding values:

```
Map<String> map = new TrieMap<String>(ConcurrencyJre.create());

map.put("my/path1", "value1");
map.put("my/path2", "value2");
```

For retrieving values, use:

```
map.getSubValues("/my"); // will return ["my/path1", "my/path2"]
map.getSubMap("/my"); // will return two Entry objects of the entries in the map with their key and value
```

