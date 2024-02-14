package org.chongwm;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.StringTokenizer;

public class PullPush
{
	
	class DockerImage
	{
		private String imageName = null;
		private String imageTag = null;

		private DockerImage(String imageLine)
		{
			StringTokenizer tokens = new StringTokenizer(imageLine);
			if(tokens.countTokens()>1)
			{
				imageName=tokens.nextToken();
				imageTag=tokens.nextToken();
			}
		}
		private String getName()
		{	return imageName; 		}
		private String getTag()
		{	return imageTag;		}
		private String getImageFQN()
		{	return imageName+":"+imageTag;		}
		
	}
	
	protected String imageListFilePath =null;
	protected String awsRegion =null;
	protected String logFilePath =null;

	public PullPush(String imageListFilePath, String awsRegion, String logFilePath)
	{
		this.imageListFilePath = imageListFilePath;
		this.awsRegion = awsRegion;
        this.logFilePath =logFilePath;
        runShell("echo '**************** PullPush with "+imageListFilePath+". Started **********' >> "+logFilePath);
        runShell("date --iso-8601=seconds -u | TZ='America/New_York' date +\"%Y-%m-%dT%H:%M:%S%:z\" >> "+logFilePath);
	}

	private List<DockerImage>  StringToImageArray(String str)
	{
		List<DockerImage> images = new ArrayList();
		List<String> lines = str.lines().toList();
		for (int l=0; l<lines.size(); l++)
		{
			images.add(new DockerImage(lines.get(l)));
		}
		return images;
	}
	
	private String LoginToECR(String localRepoName, String region)
	{
		String cmdString = "/tmp/GetECRURI.sh "+ localRepoName+" "+region;
		String output = runShell(cmdString);
		if ("0".compareTo(output.substring(0,output.indexOf('\n')))==0)
		{
			String repoUri = output.substring(output.indexOf('\n')+1, output.length());
			//"repositoryUri": "xxxxxxxxxx.dkr.ecr.us-east-2.amazonaws.com/quay.io/alfresco/alfresco-elasticsearch-live-indexing-metadata"
			//String region = output.split(".")[3];
					
			String repoPrefix = repoUri.substring(0,repoUri.indexOf("/"));
			String strGetDockerLogin = "aws ecr get-login-password --region "+region+" | docker login --username AWS --password-stdin "+repoPrefix;
			output = runShell(strGetDockerLogin);
			if ("0".compareTo(output.substring(0,output.indexOf('\n')))==0)
				return repoPrefix;
		}
		return null;
	}
	
	protected int PullImagesAllTags(String imageUrl)
	{
		String cmdString = null;
		
		System.out.println("Starting pull of all tags of "+ imageUrl +".");
		cmdString = "docker pull "+ imageUrl+" -a";
		// to pull selectively, use the following to get a list of all tags
		// curl -X GET -u <userName>:<password> https://<registry>/v1/repositories/<imageRepoUri>/tags
		String result = runShell(cmdString);
		Log(imageUrl+" pull returned "+result.charAt(0));
		return result.charAt(0)-'0';
	}
	
	private void Log (String logStr)
	{
        runShell("echo '"+logStr+"' >> "+this.logFilePath);
	}
	
	
	private void PushToECR(List<DockerImage> localImages)
	{
		String cmdString = null;
		
		System.out.println("Starting push to ECR. Image counter. Dot=1, colon=10, bang=50, pipe=100. Asterix is a pushFail, cane is a tagFail-pushSkipped.");
		for (int l=0; l<localImages.size(); l++)
		{
			String ecrPrefix =LoginToECR(localImages.get(l).getName(), this.awsRegion);
			if (ecrPrefix !=null)
			{   String result;
			    cmdString = "docker tag "+ localImages.get(l).getImageFQN()+" "+ecrPrefix+"/"+localImages.get(l).getImageFQN();
			    if (runShell(cmdString).charAt(0) == '0')
			    {
			    	cmdString = "docker push -q "+ecrPrefix+"/"+localImages.get(l).getImageFQN();
			    	result = runShell(cmdString);
			    	if (result.charAt(0)!='0')
			    		System.out.print("*");
			    }
			    else System.out.print("?");
			}
			if (l%100>0)
				if (l%50>0)
					if (l%10>0)
						System.out.print(".");
					else System.out.print(":");
				else System.out.print("!");
			else System.out.print("|");
		}
	}
	
	
	private static String runShell(String shellCmd)
	{
		String o =null;
		boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
		ProcessBuilder builder = new ProcessBuilder();
		if (!isWindows)
		{
			builder.command("sh", "-c", shellCmd);
		}
		builder.directory(new File(System.getProperty("user.home")));
		Process process;
		try
		{
			process = builder.start();
			StringBuilder output = new StringBuilder();
			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String line;
			while ((line = reader.readLine()) != null) 
			{
				output.append(line + "\n");
			}
			int exitVal = process.waitFor();
			if (exitVal == 0) 
			{
				//System.out.println("Success!");
				//System.out.println(output);
			} else 
			{
				//System.out.println(output+" Opps!");
			}
			o = String.valueOf(exitVal)+'\n'+output.toString();

		} catch (IOException | InterruptedException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return o;
	}
	
	protected List<String> StringListFromFile() 
	{
        List<String> lines = new ArrayList<>();
        
        try 
        {
            File file = new File(this.imageListFilePath);
            Scanner scanner = new Scanner(file);
            
            while (scanner.hasNextLine()) 
            {
                String line = scanner.nextLine();
                lines.add(line);
            }
            
            scanner.close();
            
        } catch (FileNotFoundException e) 
        {
            System.out.println("Error opening file: " + e.getMessage());
            e.printStackTrace();
        }
        return lines;
	}
	
	public static void main(String[] args)
	{//Read a list of images from a file specified in args[0] to push into an ECR registry in region specified args[1]. Logfile is args[2]
		PullPush pp = new PullPush(args[0], args[1], args[2]);
		List<String> imageUriList = pp.StringListFromFile();
		for (String imageUri:imageUriList)
		{				
			if (pp.PullImagesAllTags(imageUri) == 0)
		
//		String strGetDockerLogin = "aws ecr get-login-password --region "+region+" | docker login --username AWS --password-stdin "+ecrPrefix;
//		String output = runShell(strGetDockerLogin);
//		if ("0".compareTo(output.substring(0,output.indexOf('\n')))==0)
			{
				String output = runShell("docker image ls | grep "+imageUri);
				if ("0".compareTo(output.substring(0,output.indexOf('\n')))==0)
				{
					output = output.substring(output.indexOf('\n')+1, output.length());
					List<DockerImage> imageList = pp.StringToImageArray(output);
					pp.PushToECR(imageList);
			        runShell("date --iso-8601=seconds -u | TZ='America/New_York' date +\"%Y-%m-%dT%H:%M:%S%:z\" >> "+pp.logFilePath);
				}
			}	
			else
			{//no point pushing if the pull is incomplete
				System.out.println("!*!*!*!*!*!*!*!* "+imageUri+" had pull errors *!*!*!*!*!*!*!*!");
			}
		}

	}

}
