package eu.stamp.botsing.preprocessing;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class Main {

	static public Options options = initOptions();

	public static Options initOptions() {
		Options opt = new Options();
		// define options
		Option flatten = new Option("f", "flatten", false, "use this option to flatten the stack trace");
		Option error = new Option("e", "error_message", false, "use this option to remove the error message");
		Option annotations = new Option("a", "annotations", false, "UNSUPPORTED: use this option to remove the frames related to annotations");
		flatten.setType(Boolean.class);
		Option crash_log = new Option("l", "crash_log", true, "path to the input stack trace");
		Option output_log = new Option("o", "output_log", true, "path to the output stack trace after processing");
		opt.addOption(flatten);
		opt.addOption(crash_log);
		opt.addOption(output_log);
		opt.addOption(error);
		opt.addOption(annotations);
		return opt;
	}

	public static void main(String[] args) throws ParseException, FileNotFoundException{
		CommandLineParser parser = new DefaultParser();
		try {
			CommandLine cli = parser.parse(options, args);
			boolean f = cli.hasOption('f');
			boolean e = cli.hasOption('e');
			boolean a = cli.hasOption('a');
			String input = cli.getOptionValue('l');
			String output = cli.getOptionValue('o');
			preprocess(f, e, a, input, output);
		} catch (ParseException e) {
			System.out.println("wrong arguments. Available options are:");
			System.out.println(options.toString());
			throw e;
		} catch (FileNotFoundException e1) {
			System.out.println(e1.getMessage());
            throw e1;
		}
	}

	/**
	 * Performs the pre-processing on the stack trace based on the options
	 * @param flatten
	 * 			if true, a chained stack trace is flattened
	 * @param input
	 * 			input file path
	 * @param output
	 *          output file path
	 */
	public static void preprocess(boolean flatten, boolean annotations, boolean eroror, String input, String output) throws FileNotFoundException {
		File inputFile = new File(input);
		if (!inputFile.exists()) {
			throw new FileNotFoundException("Input file does not exist! Exiting...");
		}
		File outFile = new File(output);
		if (outFile.exists()) {
			throw new FileNotFoundException("Output file already exists! Exiting...");
		}
		List<String> lines = fileToLines(inputFile);
		if (flatten) {
			lines = StackFlatten.get().preprocess(lines);
		}
		if (eroror) {
			lines = ErrorMessage.get().preprocess(lines);
		}
		if (annotations) {
        }
		linesToFile(lines, outFile);
	}

	/**
	 * reads a file and returns a list of lines for the content
	 *
	 * @param logPath
	 *            path of the file
	 * @return List of lines
	 */
	static List<String> fileToLines(File log) {
		List<String> lines = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(new FileReader(log))) {
			// returns as stream and convert it into a List
			lines = reader.lines().collect(Collectors.toList());
			reader.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return lines;
	}

	/**
	 * reads a list of lines and saves them to a file
	 *
	 * @param lines
	 * 	List of lines
	 * @param out
	 *            path of the file
	 */
	static File linesToFile(List<String> lines, File out) {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(out))) {
			lines.forEach(l -> {
				try {
					writer.write(l);
					writer.newLine();
				} catch (IOException e) {
					e.printStackTrace();
				}
			});
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return out;
	}

}
