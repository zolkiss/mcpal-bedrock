import controller.Server;
import model.Variables;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static model.Variables.BACKUP_PATH_DEFAULT_VALUE;
import static model.Variables.BACKUP_PATH_PREFIX;
import static model.Variables.BEDROCK_SERVER_COMMANDS_PREFIX;
import static model.Variables.CONFIG_FILENAME;
import static model.Variables.SERVER_FILE_NOT_FOUND;
import static model.Variables.SERVER_PATH_DEFAULT_VALUE;
import static model.Variables.SERVER_PATH_PREFIX;
import static model.Variables.SERVER_PROPERTIES_NAME;
import static model.Variables.SERVER_PROPERTIES_TEMPLATE_NAME;

public class Bootstrapper {
  private final Path mcPalLocationDir;
  private String backupPath;
  private String serverPath;
  private Properties serverProperties = new Properties();
  private Properties overriddenServerProperties = new Properties();
  private Path serverPropertyPath;
  private Path serverPropertyTemplatePath;
  private List<String> bedrockServerCommands;

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
    } else if (Files.exists(mcPalLocationDir.resolve(CONFIG_FILENAME))) {
      final List<String> arguments = Files.readAllLines(mcPalLocationDir.resolve(CONFIG_FILENAME));
      Files.delete(mcPalLocationDir.resolve(CONFIG_FILENAME));
      extractArgumentsFromCommandLine(arguments);
    } else {
      throwInvalidStartArgumentsException();
    }

    processServerProperties();
  }

  private void processServerProperties() throws IOException {
    if (!Files.exists(serverPropertyTemplatePath)) {
      if (Files.exists(serverPropertyPath)) {
        System.out.printf("No %s found. Creating copy from the current server.properties%n", SERVER_PROPERTIES_TEMPLATE_NAME);
        Files.copy(serverPropertyPath, serverPropertyTemplatePath);
      } else {
        System.err.printf("Please provide %s or %s file%n", SERVER_PROPERTIES_NAME, SERVER_PROPERTIES_TEMPLATE_NAME);
        System.exit(1);
      }
    }
    Files.deleteIfExists(serverPropertyPath);
    try (FileReader fileReader = new FileReader(serverPropertyTemplatePath.toFile())) {
      serverProperties.load(fileReader);
    }

    overwriteValidServerProperties();

    try (FileWriter fileWriter = new FileWriter(serverPropertyPath.toFile())) {
      serverProperties.store(fileWriter, "Storing update properties");
    }
  }

  private void overwriteValidServerProperties() {
    if (!overriddenServerProperties.isEmpty()) {
      System.out.println("Overriding default server properties from template");
    }
    Map<Object, Object> invalidProperties = new HashMap<>();
    overriddenServerProperties.forEach((key, value) -> {
      if (serverProperties.containsKey(key)) {
        System.out.printf("- %s -> %s [%s]%n", key, value, serverProperties.get(key));
        serverProperties.put(key, value);
      } else {
        invalidProperties.put(key, value);
      }
    });

    if (!invalidProperties.isEmpty()) {
      System.out.println("Invalid properties in parameters");
      invalidProperties.forEach((key, value) -> {
        System.out.printf("- %s -> %s%n", key, value);
      });
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
    bedrockServerCommands = extractBedrockServerCommands(arguments);
    getServerProperties(arguments);
    serverPropertyPath = Paths.get(serverPath, SERVER_PROPERTIES_NAME);
    serverPropertyTemplatePath = Paths.get(serverPath, SERVER_PROPERTIES_TEMPLATE_NAME);
    if (backupPath.isEmpty()) throwInvalidStartArgumentsException();
  }

  private void getServerProperties(List<String> arguments) {
    arguments.stream()
        .filter(this::notReservedCommand)
        .filter(argument -> argument.startsWith("--"))
        .map(argument -> argument.substring(2))
        .map(argument -> {
          String propertyKey = argument.split("=")[0];
          String propertyValue = argument.substring(propertyKey.length() + 1);
          return Arrays.asList(propertyKey, propertyValue);
        })
        .forEach(argumentKeyValueList -> overriddenServerProperties.put(argumentKeyValueList.get(0), argumentKeyValueList.get(1)));

  }

  private boolean notReservedCommand(String argument) {
    return !(
        argument.startsWith(BACKUP_PATH_PREFIX)
            || argument.startsWith(SERVER_PATH_PREFIX)
            || argument.startsWith(BEDROCK_SERVER_COMMANDS_PREFIX)
    );
  }

  private void bootServer() throws IOException {
    final Server mcPal = new Server(mcPalLocationDir, backupPath, serverPath, bedrockServerCommands);
    mcPal.start();
  }

  private static List<String> extractBedrockServerCommands(List<String> arguments) {
    return arguments.stream()
        .filter(a -> a.startsWith(BEDROCK_SERVER_COMMANDS_PREFIX))
        .map(arg -> arg.split("=")[1])
        .collect(Collectors.toList());
  }

  private static String extractSingleArgument(List<String> arguments, String argumentPrefix, String defaultValue) {
    return arguments.stream()
        .filter(arg -> arg.startsWith(argumentPrefix))
        .findFirst()
        .map(arg -> arg.split("=")[1])
        .orElse(defaultValue);
  }

  private static void throwInvalidStartArgumentsException() {
    throw new IllegalStateException(Variables.INVALID_INPUT_PARAMETERS);
  }

  private static void writeConfigFile(Path fromPath, String[] args) throws IOException {
    String configContent = Stream.of(args)
        .map(arg -> String.format("%s%n", arg))
        .collect(Collectors.joining());
    Files.write(
        Paths.get(fromPath.toString(), CONFIG_FILENAME),
        configContent.getBytes(StandardCharsets.UTF_8),
        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING
    );
  }

}
