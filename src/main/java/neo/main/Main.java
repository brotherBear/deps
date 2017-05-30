package neo.main;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;

public class Main {

    static class Dependency {
        private String group;
        private String artifact;
        private String packaging;
        private String version;
        private String scope;

        private Dependency usedBy;

        void usedBy(Dependency other) {
            if (this.usedBy == null)
                this.usedBy = other;
        }

        Dependency(Dependency parent, String... properties) {
            this(properties);
            if (parent != null) {
                usedBy = parent;
            }

        }

        Dependency(String[] properties) {
            for (int i = 0; i < properties.length; i++) {
                switch (i) {
                case 0:
                    String stripped = properties[i].replaceAll("\\+- ", "");
                    group = stripped;
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
        }

        public String insert() {
            return String.format("Merge (a:Package {group: '%s', artifact: '%s', version: '%s'})", group, artifact,
                    version);
        }

        public String connect() {
            String returnval = "";
            if (usedBy != null) {
                String createRelation = String.format("Match(a%s), (b%s)\nMerge (a) -[:%s]->(b) ", usedBy.matcher(),
                        this.matcher(), this.scope);
                returnval = createRelation;
            }
            return returnval;
        }

        public String matcher() {
            return String.format(":Package {group: '%s', artifact: '%s', version: '%s'}", group, artifact, version);
        }

        public String toString() {
            return String.format("g: %s, a: %s, p: %s, v: %s, s:%s\n", group, artifact, packaging, version, scope);
        }
    }

    static private final Driver driver = GraphDatabase.driver("bolt://localhost", AuthTokens.basic("neo4j", "eleva2r"));

    public static void main(String[] args) {
        List<String> lines = readFile();
        Stack<Dependency> stack = new Stack<Dependency>();
        int level = 0;

        for (String line : lines) {
            if (line.startsWith("[INFO] --"))
                continue;
            if (line.contains("BUILD SUCCESS"))
                break;

            String stripped = line.replaceFirst("\\[INFO\\] ", "");

            processLine(stripped, stack, level, null);

        }

        driver.close();

    }

    private static void processLine(String line, Stack<Dependency> stack, int level, Dependency root) {
        if (!stack.isEmpty() && stack.size() > level) {
            root = stack.get(level);
        }
        int nextLevel = level+1;
        if (line.startsWith("+- ")) {
            while(stack.size() > nextLevel)
                stack.pop();
            processLine(line.replaceFirst("\\+- ", ""), stack, nextLevel, root);
        } else if (line.startsWith("\\- ")) {
            while(stack.size() > nextLevel)
                stack.pop();
            processLine(line.replaceFirst("\\\\- ", ""), stack, nextLevel, root);
        } else if (line.startsWith("|  ")) {
            processLine(line.replaceFirst("\\|  ", ""), stack, nextLevel, root);
        } else if (line.startsWith("   ")) {
            processLine(line.trim(), stack, nextLevel, root);
        } else {
            String[] chops = line.split(":");

            if (chops != null && chops.length > 3) {
                
                Dependency dependency = new Dependency(root, chops);
                stack.push(dependency);
                System.out.println(dependency.insert());
                System.out.println(dependency.connect());
                pushToNeo(dependency);
            }
        }

    }

    private static void pushToNeo(Dependency dep) {
        Session session = driver.session();
        session.run(dep.insert());
        if (dep.connect() != "") session.run(dep.connect());
        session.close();
    }

    private static List<String> readFile() {
        List<String> lines = new ArrayList<>();
        Path path = FileSystems.getDefault().getPath("./src/main/resources", "dep-tree-wc.txt");
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

}
