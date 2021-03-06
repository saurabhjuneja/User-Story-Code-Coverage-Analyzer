package com.impetus.codecoverage.parser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import com.impetus.codecoverage.model.LogEntry;

/**
 * The Class SVNCodeCoverage is used for create a logEntry file and get a root path.
 */
public class SVNCodeCoverage implements CodeCoverage {

    /** The Constant LOGGER. */
    private static final Logger LOGGER = Logger.getLogger(SVNCodeCoverage.class);
    
    /** The file line no map. */
    Map<String, ArrayList<String>> fileLineNoMap = null;
    
    /** The log entries. */
    List<LogEntry> logEntries = null;
    
    /** The one time work. */
    private boolean oneTimeWork = false;

    /**
     * This method is used to write xml formatted svn logs of the project root into a file
     *
     * @param pathToSrc the path to src
     * @param fileName the file name
     * 
     */
    private void writeLogToFile(String pathToSrc, String fileName) {
        String s = null;
    
        try {

            Process p = Runtime.getRuntime().exec("svn log --xml --verbose --stop-on-copy " + pathToSrc);

            BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));

            File file = new File(fileName);
            if(!file.exists()) {
            	file.getParentFile().mkdirs();
            	file.createNewFile();
            }
            
            FileOutputStream is = new FileOutputStream(file, false);
            OutputStreamWriter osw = new OutputStreamWriter(is);
            Writer w = new BufferedWriter(osw);
            while ((s = stdInput.readLine()) != null) {
                w.write(s + "\n");
            }
            w.close();

            LOGGER.debug("Logs Created Successfully.....\n\n");
        } catch (IOException e) {
       	 LOGGER.error("Here exception is coming in file operation");
            LOGGER.error(e);
        }
    }

    /**
     * This method read svn log in xml format from file and create a list of LogEntry object for each log entry in xml
     *
     * @param fileName the file name
     * @return the list
     * 
     */
    private List<LogEntry> createLogEntryFromLogFile(String fileName) {
         logEntries = new ArrayList();
        LogEntry currLogEntry = null;
        String tagContent = null;
        FileReader fr = null;

        try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            fr = new FileReader(fileName);
            XMLStreamReader reader = factory.createXMLStreamReader(fr);

            String currRevision = new String();
            String currAuthor = new String();
            String currMessage = new String();
            String path = new String();
            List<String> paths = new ArrayList();
            while (reader.hasNext()) {
                int event = reader.next();

                switch (event) {
                    case XMLStreamConstants.START_ELEMENT:
                        if ("logentry".equals(reader.getLocalName())) {
                            currRevision = reader.getAttributeValue(0);
                        }

                        if ("paths".equals(reader.getLocalName())) {
                            currLogEntry = new LogEntry();
                            currLogEntry.setAuthor(currAuthor);
                            currLogEntry.setRevision(currRevision);

                            paths = new ArrayList();
                        }
                        if ("path".equals(reader.getLocalName())) {
                            path = reader.getElementText().trim();
                            if (paths != null) {
                            	paths.add(path);
                            }
                        }
                        break;

                    case XMLStreamConstants.CHARACTERS:
                        tagContent = reader.getText().trim();
                        break;

                    case XMLStreamConstants.END_ELEMENT:
                        switch (reader.getLocalName()) {
                            case "logentry":
                                currRevision = "";
                                currAuthor = "";
                                currMessage = "";
                                logEntries.add(currLogEntry);
                                break;
                            case "path":
                                if (paths != null) {
                                    paths.add(path);
                                }
                                break;
                            case "author":
                                currAuthor = tagContent;
                                break;
                            case "msg":
                                currMessage = tagContent;
                                if(currMessage!=null)
                                {
                                currLogEntry.setMsg(currMessage);
                                }
                                else
                                {
                                 LOGGER.log(null, "Nillpointer execepition here!!");
                                }
                                break;
                            case "paths":
                            	 if(currLogEntry!=null)
                                 {
                                 	currLogEntry.setPaths(paths);
                                 }
                                 else
                                 {
                                 	  LOGGER.log(null, "Nillpointer execepition here!!");
                                        }
                                break;
                            default:
                        }
                        break;
                    default:
                }

            }

        } catch (Exception e) {
            LOGGER.error(e);
        } finally {
            try {
                if (fr != null)
                    fr.close();
            } catch (Exception p) {
                LOGGER.error(p);
            }
        }
        return logEntries;
    }
    /* 
     * @see com.codecoverage.parser.CodeCoverage#getFileLineNoMap(java.lang.String, java.lang.String)
     * 
     * In its first time execution, write svn project logs to a file and create a list of logEntries
     * From the list of log entries, create a map with revision as key and list of files as values.
     * using above map create another map with file as key and list of line numbers changed for the given user story as value
     * Returns the file line number map
     * 
     */
    @Override
    public Map<String, ArrayList<String>> getFileLineNoMap(String pathToSrc, String userStory) {

        final String fileName = "./xml/log/svnLog.xml";
        
        if (!oneTimeWork){
        	fileLineNoMap = new HashMap();
        	writeLogToFile(pathToSrc, fileName);
        	logEntries = createLogEntryFromLogFile(fileName);
        	oneTimeWork = true;
        }                        
        
        Map<String, ArrayList<String>> revisionFilesMap = new HashMap();

        for (LogEntry logEntry : logEntries) {

            if (logEntry != null && logEntry.getMsg().contains(userStory)) {
                revisionFilesMap.put(logEntry.getRevision(), (ArrayList<String>) logEntry.getPaths());
            }

        }

        fileLineNoMap = computeFileLineNoMap(pathToSrc, revisionFilesMap);
        LOGGER.debug("fileLineNoMap.toString()" + fileLineNoMap.toString());

        return fileLineNoMap;
    }
    
    /**
     * This method writes the svn info as xml to a file to determine the root path of svn repository
     *
     * @param pathToSrc the path to src
     * @return the root path
     */
    private String getRootPath(String pathToSrc){
    	Process p = null;
    	String s = null;
    	final String fileToRead = "./xml/info.xml";
    	try{
    		p = Runtime.getRuntime().exec("svn info --xml " + pathToSrc);
    		File file = new File(fileToRead);
    		
    		if(!file.exists()) {
            	file.getParentFile().mkdirs();
            	file.createNewFile();
            }
    		BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
    		
    		FileOutputStream is = new FileOutputStream(file, false);
            OutputStreamWriter osw = new OutputStreamWriter(is);
            Writer w = new BufferedWriter(osw);
            while ((s = stdInput.readLine()) != null) {
                w.write(s + "\n");
            }
            w.close();
            
            DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
            try {
                DocumentBuilder builder = domFactory.newDocumentBuilder();
                Document dDoc = builder.parse(new File(fileToRead));
                XPath xPath = XPathFactory.newInstance().newXPath();
                return xPath.compile("/info/entry/repository/root").evaluate(dDoc);
            } catch (Exception e) {
            	LOGGER.error(e);
            }
            
    	} catch (IOException e) {
            LOGGER.error(e);
        }	
    	return null;
    }

    /* 
     * This method takes path to src folder and a map with revision as key and list of files changed in that revision as value for each key
     * It blames each of the file of that revision to determine the line numbers of the file changed for the given revision
     * Since this revision is the one that is changed for the given user story
     * we ultimately are able to build and return a map with file name as key and list of line number changed in the given file for a user story as value  
     * 
     * @see com.codecoverage.parser.CodeCoverage#computeFileLineNoMap(java.lang.String, java.util.Map)
     * 
     */
    @Override
    public Map<String, ArrayList<String>> computeFileLineNoMap(String pathToSrc, Map<String, ArrayList<String>> revisionFilesMap) {
        String s ;
        String postFix;
        String fileToRead = new String();
        if (pathToSrc != null) {
            pathToSrc = pathToSrc.replace("/src/", "");
        }
        List<String> lines = new ArrayList();
        Process p;
        Map<String, ArrayList<String>> fileLineMap = new HashMap();
        
        String rootPath = getRootPath(pathToSrc);

        for (Map.Entry<String, ArrayList<String>> entry : revisionFilesMap.entrySet()) {
            String revision = entry.getKey();
            List<String> fileList = entry.getValue();
            for (String fileName : fileList) {
                try {
                    if (fileName != null && (!fileName.endsWith(".java") || fileName.endsWith("Test.java"))) {
                        continue;
                    }

                    postFix = fileName.substring(fileName.lastIndexOf("/") + 1, fileName.indexOf("."));
                    LOGGER.debug("svn blame --xml " + pathToSrc + "" + fileName);
                    File file1 = new File(fileName);
                    if(!file1.exists()) {
                    	p = Runtime.getRuntime().exec("svn blame --xml " + rootPath + fileName);
                    }else{
                    	p = Runtime.getRuntime().exec("svn blame --xml " + pathToSrc + "" + fileName);
                    }
                    BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    File file = new File("./xml/blame/svnBlame_" + postFix + ".xml");
                    fileToRead = "./xml/blame/svnBlame_" + postFix + ".xml";
                    if(!file.exists()) {
                    	file.getParentFile().mkdirs();
                    	file.createNewFile();
                    }
                    
                    LOGGER.debug(" Created file computeFileLineNoMap PATH"+file.getAbsolutePath());
                    FileOutputStream is = new FileOutputStream(file, false);
                    OutputStreamWriter osw = new OutputStreamWriter(is);
                    Writer w = new BufferedWriter(osw);
                    while ((s = stdInput.readLine()) != null) {
                        w.write(s + "\n");
                    }
                    w.close();

                } catch (IOException e) {
                    LOGGER.error(e);
                }

                lines = (ArrayList<String>) SVNBlameStax.persistBlame(fileToRead, revision, fileName);

                List<String> prevLines = fileLineMap.get(fileName);
                if (prevLines != null) {
                    lines.addAll(prevLines);
                }
                fileLineMap.put(fileName, (ArrayList<String>) lines);
            }

        }

        return fileLineMap;
    }

}
