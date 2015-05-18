import com.midvision.rapiddeploy.connector.RapidDeployConnector;

final def inputProps = new Properties();
final def inputPropsFile = new File(args[0]);

try{
	inputPropsStream = new FileInputStream(inputPropsFile);
	inputProps.load(inputPropsStream);
} catch (IOException e) {
	throw new RuntimeException(e);
}

final def serverUrl = inputProps['serverUrl'];
final def authToken = inputProps['authToken'];
final def project = inputProps['project'];
final def environment = inputProps['environment'];
final def packageName = inputProps['package'];
final def userName = inputProps['userName'];
final def passwordEncrypted = inputProps['passwordEncrypted'];
final def keyFilePath = inputProps['keyFilePath'];
final def keyPassPhraseEncrypted = inputProps['keyPassPhraseEncrypted'];
final def encryptionKey = inputProps['encryptionKey'];

RapidDeployConnector.invokeRapidDeployDeploymentPollOutput(authToken, serverUrl, project, environment, packageName, true, userName, passwordEncrypted, keyFilePath, keyPassPhraseEncrypted, encryptionKey);

System.exit(0);