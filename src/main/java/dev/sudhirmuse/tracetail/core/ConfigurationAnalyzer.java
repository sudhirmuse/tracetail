/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public final class ConfigurationAnalyzer {
    public String compare(Path left, Path right) throws IOException { Map<String,String> a=flatten(left),b=flatten(right); java.util.Set<String> keys=new java.util.TreeSet<>(a.keySet());keys.addAll(b.keySet()); LogRedactor redactor=new LogRedactor(); StringBuilder out=new StringBuilder("EFFECTIVE CONFIGURATION COMPARISON\n\n"); for(String key:keys) if(!java.util.Objects.equals(a.get(key),b.get(key))) out.append(key).append("\n  left: ").append(mask(key,a.getOrDefault(key,"<missing>"),redactor)).append("\n  right: ").append(mask(key,b.getOrDefault(key,"<missing>"),redactor)).append("\n"); return out.toString(); }
    public Map<String,String> merge(List<Path> sources) throws IOException { Map<String,String> effective=new LinkedHashMap<>(); for(Path source:sources) effective.putAll(flatten(source)); return effective; }
    private Map<String,String> flatten(Path path)throws IOException{String name=path.getFileName().toString().toLowerCase();String text=Files.readString(path);if(name.endsWith(".properties")||name.endsWith(".ini")||name.endsWith(".conf")){Properties values=new Properties();values.load(new StringReader(text));Map<String,String> out=new LinkedHashMap<>();values.stringPropertyNames().stream().sorted().forEach(key->out.put(key,values.getProperty(key)));return out;} ObjectMapper mapper=name.endsWith(".yaml")||name.endsWith(".yml")?new YAMLMapper():new ObjectMapper();JsonNode root=mapper.readTree(text);Map<String,String> out=new LinkedHashMap<>();flatten("",root,out);return out;}
    private static void flatten(String prefix,JsonNode node,Map<String,String> out){if(node.isValueNode()){out.put(prefix,node.asText());return;}if(node.isArray()){for(int i=0;i<node.size();i++)flatten(prefix+"["+i+"]",node.get(i),out);return;}node.properties().forEach(entry->flatten(prefix.isEmpty()?entry.getKey():prefix+"."+entry.getKey(),entry.getValue(),out));}
    private static String mask(String key,String value,LogRedactor redactor){return key.toLowerCase().matches(".*(?:password|passwd|secret|token|api[-_.]?key|authorization|cookie).*")?"<redacted>":redactor.redact(value);}
}
