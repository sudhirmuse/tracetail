/* Copyright 2026 Sudhir Mishra. SPDX-License-Identifier: Apache-2.0 */
package dev.sudhirmuse.tracetail.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

public final class ParserProfileStore {
    public ParserProfile load(Path path) throws IOException { ObjectMapper mapper = path.getFileName().toString().toLowerCase().endsWith(".json") ? new ObjectMapper() : new YAMLMapper(); ParserProfile profile = mapper.readValue(path.toFile(),ParserProfile.class); validate(profile); return profile; }
    public void saveTemplate(Path path) throws IOException { Files.createDirectories(path.toAbsolutePath().getParent()); new YAMLMapper().writeValue(path.toFile(), new ParserProfile("My service","^(?<timestamp>\\S+)","(?<level>TRACE|DEBUG|INFO|WARN|ERROR)","\\[(?<thread>[^]]+)]","traceId=(?<trace>[A-Za-z0-9-]+)",List.of("service","host"))); }
    public String test(ParserProfile profile, String line) { return "Profile: " + profile.name() + "\nTimestamp: " + group(profile.timestampRegex(),line,"timestamp") + "\nLevel: " + group(profile.levelRegex(),line,"level") + "\nThread: " + group(profile.threadRegex(),line,"thread") + "\nTrace: " + group(profile.traceRegex(),line,"trace") + "\nColumns: " + profile.columns(); }
    private static String group(String regex,String line,String name) { if (regex==null||regex.isBlank()) return ""; var matcher=Pattern.compile(regex).matcher(line); if(!matcher.find()) return "not matched"; try{return matcher.group(name);}catch(IllegalArgumentException exception){return matcher.group();} }
    private static void validate(ParserProfile profile) { if(profile.name()==null||profile.name().isBlank()) throw new IllegalArgumentException("Profile name is required"); for(String regex:List.of(safe(profile.timestampRegex()),safe(profile.levelRegex()),safe(profile.threadRegex()),safe(profile.traceRegex()))) if(!regex.isBlank()) Pattern.compile(regex); }
    private static String safe(String value){return value==null?"":value;}
}
