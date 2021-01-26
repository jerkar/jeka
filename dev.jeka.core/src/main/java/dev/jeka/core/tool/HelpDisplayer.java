package dev.jeka.core.tool;

import dev.jeka.core.api.system.JkLog;
import dev.jeka.core.api.utils.*;
import dev.jeka.core.tool.PluginDictionary.PluginDescription;
import dev.jeka.core.tool.ProjectDef.RunClassDef;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

final class HelpDisplayer {

    static void help(JkCommandSet jkCommandSet) {
        if (JkOptions.containsKey("Plugins")) {
            helpPlugins(jkCommandSet);
            return;
        }
        StringBuilder sb = new StringBuilder()
                .append("Usage: \n\njeka (method | pluginName#method) [-optionName=<value>] [-pluginName#optionName=<value>] [-DsystemPropName=value]\n\n")
                .append("Executes the specified methods defined in commandSet class or plugins using the specified options and system properties.\n\n")
                .append("Ex: jeka clean java#pack -java#pack.sources=true -LogVerbose -other=xxx -DmyProp=Xxxx\n\n")
                .append(standardOptions())
                .append("\nAvailable methods and options :\n")
                .append(RunClassDef.of(jkCommandSet).description());

        // List plugins
        final Set<PluginDescription> pluginDescriptions = new PluginDictionary().getAll();
        List<String> names = pluginDescriptions.stream().map(pluginDescription -> pluginDescription.shortName()).collect(Collectors.toList());
        sb.append("\nAvailable plugins in classpath : ").append(JkUtilsString.join(names, ", "))
                .append(".\n");

        sb.append("\nType 'jeka [pluginName]#help' to get help on a particular plugin (ex : 'jeka java#help'). ");
        sb.append("\nType 'jeka help -Plugins' to get help on all available plugins in the classpath.\n");
        JkLog.info(sb.toString());
    }

    private static String standardOptions() {
        StringBuilder sb = new StringBuilder();
        sb.append("Built-in options (these options are not specific to a plugin or a command class) :\n");
        sb.append("  -LogVerbose (shorthand -LV) : logs 'trace' level.\n");
        sb.append("  -LogQuiteVerbose (shorthand -LQV) : logs 'trace' level + Ivy trace level.\n");
        sb.append("  -LogRuntimeInfo (shorthand -LRI) : logs Jeka runtime information.\n");
        sb.append("  -LogHeaders (shorthand -LH) : logs intro and def classes compilation steps.\n");
        sb.append("  -LogMaxLength (shorthand -LML) : Console will do a carriage return automatically after N characters are outputted in a single line (ex : -LML=120).\n");
        sb.append("  -CommandClass (shorthand -CC) : Force to use the specified class as the command class to invoke. It can be the short name of the class (without package prefix).\n");
        return sb.toString();
    }

    static void help(JkCommandSet run, Path xmlFile) {
        final Document document = JkUtilsXml.createDocument();
        final Element runEl = RunClassDef.of(run).toElement(document);
        document.appendChild(runEl);
        if (xmlFile == null) {
            JkUtilsXml.output(document, System.out);
        } else {
            JkUtilsPath.createFile(xmlFile);
            try (final OutputStream os = Files.newOutputStream(xmlFile)) {
                JkUtilsXml.output(document, os);
            } catch (final IOException e) {
                throw JkUtilsThrowable.unchecked(e);
            }
            JkLog.info("Xml help file generated at " + xmlFile);
        }
    }

    private static void helpPlugins(JkCommandSet jkCommandSet) {
        JkLog.info(helpPluginsDescription(jkCommandSet));
    }

    static void helpPlugin(JkPlugin plugin) {
        final Set<PluginDescription> pluginDescriptions = new PluginDictionary().getAll();
        for (PluginDescription pluginDescription : pluginDescriptions) {
            if (pluginDescription.shortName().equals(plugin.name())) {
                JkLog.info(helpPluginDescription(plugin.getCommandSet(), pluginDescription));
                return;
            }
        }
    }

    private static String helpPluginsDescription(JkCommandSet jkCommandSet) {
        final Set<PluginDescription> pluginDescriptions = new PluginDictionary().getAll();
        StringBuilder sb = new StringBuilder();
        for (final PluginDescription description : pluginDescriptions) {
            sb.append(helpPluginDescription(jkCommandSet, description));
        }
        return sb.toString();
    }

    private static String helpPluginDescription(JkCommandSet jkCommandSet, PluginDescription description) {
        StringBuilder sb = new StringBuilder();
        sb.append("\nPlugin Class : " + description.fullName());
        sb.append("\nPlugin Name : " + description.shortName());
        List<String> deps = description.pluginDependencies();
        if (!deps.isEmpty()) {
            sb.append("\nDepends on plugins : " + JkUtilsString.join(deps, ", "));
        }
        final List<String> explanations = description.explanation();
        if (!explanations.isEmpty()) {
            sb.append("\nPurpose : " + description.explanation().get(0));
            description.explanation().subList(1, description.explanation().size()).forEach(
                    line -> sb.append("\n          " + line));
        }
        final List<String> activationEffects = description.activationEffect();
        if (!activationEffects.isEmpty()) {
            sb.append("\nActivation Effects : " + description.activationEffect().get(0));
            description.explanation().subList(1, description.activationEffect().size()).forEach(
                    line -> sb.append("\n                      " + line));
        } else if (!description.isDecorateRunDefined()){
            sb.append("\nActivation Effect : None.");
        } else {
            sb.append("\nActivation Effect : Not documented.");
        }
        final JkPlugin plugin;
        if (jkCommandSet.getPlugins().hasLoaded(description.pluginClass())) {
            plugin = jkCommandSet.getPlugin(description.pluginClass());
        } else {
            plugin = JkUtilsReflect.newInstance(description.pluginClass(), JkCommandSet.class, jkCommandSet);
        }
        sb.append("\n");
        sb.append(RunClassDef.of(plugin).flatDescription(description.shortName() + "#"));
        return sb.toString();
    }

    static List<String> optionValues(List<ProjectDef.CommandOptionDef> optionDefs) {
        return optionDefs.stream().map(optionDef -> optionDef.shortDescription()).collect(Collectors.toList());
    }

}
