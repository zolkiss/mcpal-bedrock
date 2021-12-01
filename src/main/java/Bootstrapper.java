import controller.Server;
import model.Variables;

import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static model.Variables.ADDITIONAL_ARGUMENT_PREFIX;
import static model.Variables.BACKUP_PATH_DEFAULT_VALUE;
import static model.Variables.BACKUP_PATH_PREFIX;
import static model.Variables.CONFIG_FILENAME;
import static model.Variables.SERVER_FILE_NOT_FOUND;
import static model.Variables.SERVER_PATH_DEFAULT_VALUE;
import static model.Variables.SERVER_PATH_PREFIX;

public class Bootstrapper {

    private final Path mcPalLocationDir;
    private String backupPath;
    private String serverPath;
    private List<String> additionalCommandsToRunAfterBackup;

    public static void main(String[] args) throws URISyntaxException, IOException {
        final Bootstrapper main = new Bootstrapper(args);
        main.bootServer();
    }

    public Bootstrapper(String... args) throws IOException, URISyntaxException {
        mcPalLocationDir = evaluatePathOfJar();

        if (args.length != 0) {
            final List<String> arguments = Arrays.asList(args);
            extractArgumentsFromCommandLine(arguments);
            writeConfigFile(mcPalLocationDir, args);
        } else if (args.length == 0 && Files.exists(mcPalLocationDir.resolve(CONFIG_FILENAME))) {
            final List<String> arguments = Files.readAllLines(mcPalLocationDir.resolve(CONFIG_FILENAME));
            Files.delete(mcPalLocationDir.resolve(CONFIG_FILENAME));
            extractArgumentsFromCommandLine(arguments);
        } else {
            throwInvalidStartArgumentsException();
        }
    }

    private Path evaluatePathOfJar() throws URISyntaxException {
        Path fromPath = Paths.get(Server.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
        if (!Files.exists(fromPath)) throw new IllegalArgumentException(SERVER_FILE_NOT_FOUND);
        return fromPath;
    }

    private void extractArgumentsFromCommandLine(List<String> arguments) {
        backupPath = extractSingleArgument(arguments, BACKUP_PATH_PREFIX, BACKUP_PATH_DEFAULT_VALUE);
        serverPath = extractSingleArgument(arguments, SERVER_PATH_PREFIX, SERVER_PATH_DEFAULT_VALUE);
        if (backupPath.isEmpty()) throwInvalidStartArgumentsException();
        additionalCommandsToRunAfterBackup = extractAdditionalArguments(arguments);
    }

    private void bootServer() throws IOException {
        final Server MCpal = new Server(mcPalLocationDir, backupPath, serverPath, additionalCommandsToRunAfterBackup);
        MCpal.start();
    }

    private static List<String> extractAdditionalArguments(List<String> arguments) {
        return arguments.stream()
            .filter(a -> a.startsWith(ADDITIONAL_ARGUMENT_PREFIX))
            .map(arg -> arg.substring(ADDITIONAL_ARGUMENT_PREFIX.length()))
                .collect(Collectors.toList());
    }

    private static String extractSingleArgument(List<String> arguments, String argumentPrefix, String defaultValue) {
        return arguments.stream()
            .filter(arg -> arg.startsWith(argumentPrefix))
            .findFirst()
            .map(arg -> arg.substring(argumentPrefix.length()))
            .orElse(defaultValue);
    }

    private static void throwInvalidStartArgumentsException() {
        throw new IllegalStateException(Variables.INVALID_INPUT_PARAMETERS);
    }

    private static void writeConfigFile(Path fromPath, String[] args) throws IOException {
        if (!Files.exists(fromPath.resolve(CONFIG_FILENAME)))
            Files.createFile(Paths.get(fromPath + "/" + CONFIG_FILENAME));
        final FileWriter fw = new FileWriter(fromPath + "/" + CONFIG_FILENAME);
        for (String parameter : args) {
            fw.write(parameter + System.getProperty("line.separator"));
        }
        fw.flush();
        fw.close();
    }

}
