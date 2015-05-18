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
final def packageName = inputProps['package'];

RapidDeployConnector.invokeRapidDeployBuildPackage(authToken, serverUrl, project, packageName, null, true);

System.exit(0);