package neo.main;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;

public class Main {

    static class Dependency {
        String group;
        String artifact;
        String packaging;
        String version;
        String scope;
        
        List<Dependency> children; 

        Dependency(String[] properties) {
            for (int i = 0; i < properties.length; i++) {
                switch (i) {
                case 0:
                    group = properties[i].replaceAll("\\[INFO\\] ", "");
                    break;
                case 1:
                    artifact = properties[i];
                    break;
                case 2:
                    packaging = properties[i];
                    break;
                case 3:
                    version = properties[i];
                    break;
                case 4:
                    scope = properties[i];
                    break;
                default:
                    break;
                }
            }
            children = new ArrayList<>();
        }

        public String toString() {
            return String.format("g: %s, a: %s, p: %s, v: %s, s:%s\n", group, artifact, packaging, version, scope);
        }
    }

    public static void main(String[] args) {
        List<String> lines = readFile();
        String pattern = "";
        Pattern p = Pattern.compile(pattern);

        for (String line : lines) {
            if (line.startsWith("[INFO] --"))
                continue;
            String[] chops = line.split(":");
            if (chops != null && chops.length > 3) {
                Dependency dependency = new Dependency(chops);
                System.out.println(dependency);
            }
        }

        // pushToNeo();

    }

    private static List<String> readFile() {
        List<String> lines = new ArrayList<>();
        Path path = FileSystems.getDefault().getPath("./src/main/resources", "dep-tree.txt");
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line = null;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException x) {
            System.err.format("IOException: %s%n", x);
        }
        return lines;
    }

    private static void pushToNeo() {
        Driver driver = GraphDatabase.driver("bolt://localhost", AuthTokens.basic("neo4j", "eleva2r"));

        Session session = driver.session();

        session.run("Create (a:Person {name: 'Arthur', title: 'King'})");

        StatementResult result = session
                .run("Match (a:Person) where a.name = 'Arthur' return a.name as name, a.title as title");

        while (result.hasNext()) {
            Record record = result.next();
            System.out.println(record.get("title").asString() + " " + record.get("name").asString());
        }

        session.close();
        driver.close();
    }

}
