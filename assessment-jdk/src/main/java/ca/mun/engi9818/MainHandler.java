package ca.mun.engi9818;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;


public class MainHandler {
    private static String TMP_ROOT = System.getProperty("java.io.tmpdir");
    private static String TMP_DIR_NAME = "mun-assessment";

    private static final Logger LOGGER = Logger.getLogger(MainHandler.class.getName());
    private static final StringHandler STRING_HANDLER = new StringHandler();
    static {
        LOGGER.addHandler(STRING_HANDLER);
        // LOGGER.addHandler(new ConsoleHandler());
        LOGGER.setLevel(Level.ALL);
    }

    public String handleRequest(byte[] submission, byte[] project, List<String> files) throws SubmissionHandlingException {
        STRING_HANDLER.clear();

        if (submission == null) {
            LOGGER.severe("No submission provided");
            throw new SubmissionHandlingException("No submission provided");
        }

        this.deletePrivateTempDir();

        File submissionDir = new File(this.getPrivateTempDir("submission"));
        LOGGER.info("Output directory: " + submissionDir.getAbsolutePath());

        LOGGER.info("Extracting submission...");
        try {
            // byte[] decodedBytes = Base64.getDecoder().decode(submission);
            extractZip(new ByteArrayInputStream(submission), submissionDir);
        } catch (IOException e) {
            // e.printStackTrace();
            throw new SubmissionHandlingException(
                "Unable to extract submission",
                STRING_HANDLER.getLogContent(),
                e);
        }
        LOGGER.info("Submission extracted");

        // Extract the resource files
        LOGGER.info("Extracting project...");
        File projectDir = new File(this.getPrivateTempDir("project"));
        try {
            // this.extractResource("/project.zip", projectDir);
            extractZip(new ByteArrayInputStream(project), projectDir);
        } catch (IOException e) {
            // e.printStackTrace();
            throw new SubmissionHandlingException(
                "Unable to extract project",
                STRING_HANDLER.getLogContent(),
                e);
        }
        LOGGER.info("Project extracted");

        // LOGGER.info("Moving submission files...");
        // Path source = submissionDir.toPath().resolve("DBMS.java");
        // Path target = (new File(projectDir, "src")).toPath().resolve("DBMS.java");
        // try {
        //     Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        // } catch (IOException e) {
        //     // e.printStackTrace();
        //     throw new SubmissionHandlingException(
        //         "Unable to move DBMS.java",
        //         STRING_HANDLER.getLogContent(),
        //         e);
        // }

        // LOGGER.info("Moving test files...");
        // List<File> testFiles = null;
        // try {
        //     testFiles = Files.walk(Paths.get(submissionDir.getAbsolutePath()))
        //         .filter(Files::isRegularFile)
        //         .map(Path::toFile)
        //         .filter(file -> file.getName().matches("DBMSTest\\d{9}\\.java"))
        //         .collect(Collectors.toList());
        // } catch (IOException e) {
        //     // e.printStackTrace();
        //     throw new SubmissionHandlingException(
        //         "Unable to get test files",
        //         STRING_HANDLER.getLogContent(),
        //         e);
        // }
        // if (testFiles.size() < 1) {
        //     throw new SubmissionHandlingException(
        //         "No test files found",
        //         STRING_HANDLER.getLogContent());
        // }
        // for(File f: testFiles) {
        //     source = f.toPath();
        //     target = (new File(projectDir, "src")).toPath().resolve(f.getName());
        //     try {
        //         Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        //     } catch (IOException e) {
        //         // e.printStackTrace();
        //         throw new SubmissionHandlingException(
        //             "Unable to move test file",
        //             STRING_HANDLER.getLogContent(),
        //             e);
        //     }
        // }
        LOGGER.info("Moving submission files...");
        for(String fileNamePattern: files) {
            try {
                // Create a PathMatcher using the regex
                PathMatcher matcher = FileSystems.getDefault().getPathMatcher("regex:" + fileNamePattern);

                // Iterate over files in the submission directory
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(submissionDir.toPath())) {
                    for (Path entry : stream) {
                        if (matcher.matches(entry.getFileName())) {
                            // If the file name matches the regex, move the file
                            Path target = (new File(projectDir, "src")).toPath().resolve(entry.getFileName());
                            LOGGER.info("Moving " + entry.getFileName() + " to " + target);
                            Files.move(entry, target, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }

            } catch (IOException e) {
                System.out.println(e.getMessage());
                throw new SubmissionHandlingException(
                    "Unable to move submission file",
                    STRING_HANDLER.getLogContent(),
                    e);
            }
        }
        
        LOGGER.info("Compiling project in " + projectDir.getAbsolutePath() + "...");
        try {
            String compilationOutput = compileJavaFiles(projectDir);
            LOGGER.info(compilationOutput);
        } catch (IOException | InterruptedException | RuntimeException e) {
            // e.printStackTrace();
            throw new SubmissionHandlingException(
                "Unable to compile project",
                STRING_HANDLER.getLogContent(),
                e);
        }

        LOGGER.info("Running JUnit tests...");
        try {
            String junitOutput = runJUnitTests(projectDir);
            LOGGER.info(junitOutput);
        } catch (IOException | InterruptedException e) {
            // e.printStackTrace();
            throw new SubmissionHandlingException(
                "Unable to run JUnit tests",
                STRING_HANDLER.getLogContent(),
                e);
        }

        LOGGER.info("Analysing test results...");
        Map<String, String> testResults;
        try {
            File xmlReport = new File(projectDir, "reports/TEST-junit-jupiter.xml");
            testResults = analyseTestResults(xmlReport);
            // format the map
            StringBuilder testResultsBuilder = new StringBuilder();
            for (Map.Entry<String, String> entry : testResults.entrySet()) {
                testResultsBuilder.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
            LOGGER.info(testResultsBuilder.toString());
        } catch (ParserConfigurationException | SAXException | IOException e) {
            // e.printStackTrace();
            throw new SubmissionHandlingException(
                "Unable to analyse test results",
                STRING_HANDLER.getLogContent(),
                e);
        }

        for (String result : testResults.values()) {
            if (result.equals("FAIL")) {
                throw new SubmissionHandlingException(
                    "Submission test failed",
                    STRING_HANDLER.getLogContent());
            }
        }

        LOGGER.info("Done");
        return STRING_HANDLER.getLogContent();
    }

    private void deletePrivateTempDir() {
        File tmpDir = new File(TMP_ROOT, TMP_DIR_NAME);
        if (tmpDir.exists()) {
            LOGGER.info("Deleting temp directory: " + tmpDir.getAbsolutePath());
            // remove the temp directory
            try {
                Files.walk(tmpDir.toPath())
                    .sorted(java.util.Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            } catch (IOException e) {
                LOGGER.severe("Failed to delete temp directory: " + tmpDir.getAbsolutePath());
            }
        }
    }

    private String getPrivateTempDir(String name) {
        File tmpDir = new File(TMP_ROOT, TMP_DIR_NAME);
        if (!tmpDir.exists()) {
            if (tmpDir.mkdir()) {
                LOGGER.fine("Temp directory created: " + tmpDir.getAbsolutePath());
            } else {
                LOGGER.severe("Failed to create temp directory: " + tmpDir.getAbsolutePath());
            }
        } else {
            LOGGER.fine("Temp directory already exists: " + tmpDir.getAbsolutePath());
        }

        tmpDir = new File(tmpDir, name);
        if (!tmpDir.exists()) {
            if (tmpDir.mkdir()) {
                LOGGER.fine("Temp directory created: " + tmpDir.getAbsolutePath());
            } else {
                LOGGER.severe("Failed to create temp directory: " + tmpDir.getAbsolutePath());
            }
        } else {
            LOGGER.fine("Temp directory already exists: " + tmpDir.getAbsolutePath());
        }

        return tmpDir.getAbsolutePath();
    }

    private void extractZip(InputStream zipInputStream, File outputDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(zipInputStream)) {
            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                LOGGER.fine("Extracting: " + zipEntry.getName());
                File newFile = new File(outputDir, zipEntry.getName());

                // Create directories if they don't exist
                if (zipEntry.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    // Ensure the parent directory exists
                    new File(newFile.getParent()).mkdirs();

                    // Write the file
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
            }
        }
    }

    private void extractResource(String resourceName, File outputDir) throws IOException {
        InputStream is = MainHandler.class.getResourceAsStream(resourceName);
        if (is == null) {
            throw new IllegalArgumentException("Resource not found: " + resourceName);
        }
        extractZip(is, outputDir);
    }

    private String compileJavaFiles(File projectDir) throws IOException, InterruptedException {
        // get all .java file in src/
        List<String> files = Files.walk(Paths.get(projectDir.getAbsolutePath() + "/src/"))
            .filter(Files::isRegularFile)
            .map(Path::toString)
            .collect(Collectors.toList());
        // construct the compiling command
        String[] command = new String[files.size() + 5];
        command[0] = "javac";
        for (int i = 0; i < files.size(); i++) {
            command[i + 1] = files.get(i);
        }
        command[files.size() + 1] = "-cp";
        command[files.size() + 2] = "lib/junit-platform-console-standalone-1.10.0.jar";
        command[files.size() + 3] = "-d";
        command[files.size() + 4] = "bin/";

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(projectDir);
        processBuilder.redirectErrorStream(true);
        LOGGER.info("Compiling with command: "+ String.join(" ", command));
        Process process = processBuilder.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            LOGGER.severe("Compilation failed with exit code: " + exitCode);
            throw new RuntimeException("Compilation failed with exit code: " + exitCode);
        }

        return output.toString();
    }

    private String runJUnitTests(File projectDir) throws IOException, InterruptedException {
        String[] command = {
            "java",
            "-jar",
            "lib/junit-platform-console-standalone-1.10.0.jar",
            "execute",
            "--details=verbose",
            "--classpath=bin",
            "--scan-classpath",
            "--include-classname=.*",
            "--reports-dir=reports"
        };

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(projectDir);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            LOGGER.severe("JUnit tests failed with exit code: " + exitCode);
        }

        return output.toString();
    }

    private Map<String, String> analyseTestResults(File xmlReport) throws ParserConfigurationException, SAXException, IOException {
        Map<String, String> testResults = new HashMap<>();

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(xmlReport);

        // Normalize XML structure
        doc.getDocumentElement().normalize();

        // Get all testcase elements
        NodeList testCases = doc.getElementsByTagName("testcase");

        for (int i = 0; i < testCases.getLength(); i++) {
            Node testCaseNode = testCases.item(i);

            if (testCaseNode.getNodeType() == Node.ELEMENT_NODE) {
                Element testCaseElement = (Element) testCaseNode;

                String className = testCaseElement.getAttribute("classname");
                String testName = testCaseElement.getAttribute("name");
                String fullTestName = className + " - " + testName;

                // Check for failure or error elements inside the testcase
                NodeList failureNodes = testCaseElement.getElementsByTagName("failure");
                NodeList errorNodes = testCaseElement.getElementsByTagName("error");

                if (failureNodes.getLength() > 0 || errorNodes.getLength() > 0) {
                    testResults.put(fullTestName, "FAIL");
                } else {
                    testResults.put(fullTestName, "PASS");
                }
            }
        }
        return testResults;
    }
}

class StringHandler extends Handler {
    private final StringWriter stringWriter = new StringWriter();
    private final PrintWriter printWriter = new PrintWriter(stringWriter);

    public void clear() {
        stringWriter.getBuffer().setLength(0);
    }

    @Override
    public void publish(LogRecord record) {
        if (!isLoggable(record)) {
            return;
        }
        printWriter.println(record.getMessage());
    }

    @Override
    public void flush() {
        printWriter.flush();
    }

    @Override
    public void close() throws SecurityException {
        printWriter.close();
    }

    public String getLogContent() {
        flush();
        final String ret = stringWriter.toString();
        return ret;
    }
}
