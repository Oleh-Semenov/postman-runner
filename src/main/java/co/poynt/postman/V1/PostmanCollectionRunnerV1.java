package co.poynt.postman.V1;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.poynt.postman.modelV1.PostmanCollectionV1;
import co.poynt.postman.model.PostmanEnvironment;
import co.poynt.postman.modelV1.PostmanFolderV1;
import co.poynt.postman.modelV1.PostmanRequestV1;
import co.poynt.postman.model.PostmanVariables;

public class PostmanCollectionRunnerV1 {
	private static final Logger logger = LoggerFactory.getLogger(PostmanCollectionRunnerV1.class);
	public static final String ARG_COLLECTION = "c";
	public static final String ARG_ENVIRONMENT = "e";
	public static final String ARG_FOLDER = "f";
	public static final String ARG_HALTONERROR = "haltonerror";

	private PostmanVariables sharedPostmanEnvVars;

	public static void main(String[] args) throws Exception {
		Options options = new Options();
		options.addOption(ARG_COLLECTION, true, "File name of the POSTMAN collection.");
		options.addOption(ARG_ENVIRONMENT, true, "File name of the POSTMAN environment variables.");
		options.addOption(ARG_FOLDER, true,
				"(Optional) POSTMAN collection folder (group) to execute i.e. \"My Use Cases\"");
		options.addOption(ARG_HALTONERROR, false, "(Optional) Stop on first error in POSTMAN folder.");

		CommandLineParser parser = new BasicParser();
		CommandLine cmd = parser.parse(options, args);
		String colFilename = cmd.getOptionValue(ARG_COLLECTION);
		String envFilename = cmd.getOptionValue(ARG_ENVIRONMENT);
		String folderName = cmd.getOptionValue(ARG_FOLDER);
		boolean haltOnError = cmd.hasOption(ARG_HALTONERROR);

		if (colFilename == null || colFilename.isEmpty() || envFilename == null || envFilename.isEmpty()) {
			// automatically generate the help statement
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("postman-runner", options);
			return;
		}

		PostmanCollectionRunnerV1 pcr = new PostmanCollectionRunnerV1();
		pcr.runCollection(colFilename, envFilename, folderName, haltOnError, false);
	}

	public PostmanRunResultV1 runCollection(String colFilename, String envFilename, String folderName,
											boolean haltOnError) throws Exception {
		return runCollection(colFilename, envFilename, folderName, haltOnError, false);
	}

	/**
	 *
	 * @param colFilename
	 * @param envFilename
	 * @param folderName
	 * @param haltOnError
	 * @param useSharedPostmanVars
	 *            Use a single set of postman variable(s) across all your tests.
	 *            This allows for running tests between a select few postman
	 *            folders while retaining environment variables between each run
	 * @return
	 * @throws Exception
	 */
	public PostmanRunResultV1 runCollection(String colFilename, String envFilename, String folderName,
											boolean haltOnError, boolean useSharedPostmanVars) throws Exception {
		logger.info("@@@@@ POSTMAN Runner start: {}", colFilename);
		PostmanRunResultV1 runResult = new PostmanRunResultV1();

		PostmanReaderV1 reader = new PostmanReaderV1();
		PostmanCollectionV1 c = reader.readCollectionFile(colFilename);
		c.init();
		PostmanEnvironment e = reader.readEnvironmentFile(envFilename);
		e.init();
		PostmanFolderV1 folder = null;
		if (folderName != null && !folderName.isEmpty()) {
			folder = c.folderLookup.get(folderName);
		}

		PostmanVariables var;
		if (useSharedPostmanVars) {
			if (sharedPostmanEnvVars == null) {
				sharedPostmanEnvVars = new PostmanVariables(e);
			}
			var = sharedPostmanEnvVars;
		} else {
			var = new PostmanVariables(e);
		}

		PostmanRequestRunnerV1 runner = new PostmanRequestRunnerV1(var, haltOnError);
		boolean isSuccessful = true;
		if (folder != null) {
			isSuccessful = runFolder(haltOnError, runner, var, c, folder, runResult);
		} else {
			// Execute all folder all requests
			for (PostmanFolderV1 pf : c.folders) {
				isSuccessful = runFolder(haltOnError, runner, var, c, pf, runResult) && isSuccessful;
				if (haltOnError && !isSuccessful) {
					return runResult;
				}
			}
		}

		logger.info("@@@@@ Yay! All Done!");
		logger.info(runResult.toString());
		return runResult;
	}

	private boolean runFolder(boolean haltOnError, PostmanRequestRunnerV1 runner, PostmanVariables var,
							  PostmanCollectionV1 c, PostmanFolderV1 folder, PostmanRunResultV1 runResult) {
		logger.info("==> POSTMAN Folder: " + folder.name);
		boolean isSuccessful = true;
		for (String reqId : folder.order) {
			runResult.totalRequest++;
			PostmanRequestV1 r = c.requestLookup.get(reqId);
			logger.info("======> POSTMAN request: " + r.name);
			try {
				boolean runSuccess = runner.run(r, runResult);
				if (!runSuccess) {
					runResult.failedRequest++;
					runResult.failedRequestName.add(folder.name + "." + r.name);
				}
				isSuccessful = runSuccess && isSuccessful;
				if (haltOnError && !isSuccessful) {
					return isSuccessful;
				}
			} catch (Throwable e) {
				e.printStackTrace();
				runResult.failedRequest++;
				runResult.failedRequestName.add(folder.name + "." + r.name);
				return false;
			}
		}
		return isSuccessful;
	}
}
