import java.io.*;
import java.net.URL;
import java.util.ArrayList;

public class IdcDm {


    static int CHUNKSIZE = 4096;//Constant chunk size
    static ArrayList<Range> MetaDateRangesArray = new ArrayList<>();//Meta data range list
    static ArrayList<Range> ranges = new ArrayList<>();//Ranges array

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("usage:\n \tjava IdcDm URL|URL-LIST-FILE [MAX-CONCURRENT-CONNECTIONS]");
            return;
        }

        ArrayList<String> links = new ArrayList<>();

        int threadAmount = 7;
        String link = args[0];

        if (args.length > 1) {
            threadAmount = Integer.parseInt(args[1]);
        } else {
            threadAmount = 1;
        }

        //Check if its a list of mirror servers links or 1 server link
        //If its not valid url then its a list of links
        if (isValid(link)) {
            links.add(link);
        } else {
            readaLinksFromFile(links, link);
        }

        //Extracts file name from URL
        String fileName = links.get(0).substring(links.get(0).lastIndexOf("/") + 1);

        //Check for resume download
        long totalByteRead = checkResumedDownload(fileName, threadAmount);

        //Start the download manager program
        CreateConnection.startDownload(links, threadAmount, fileName, totalByteRead);

    }

    /**
     * The method read link by link from a file with some links
     * And add each link to the links array
     *
     * @param links - Array list to set all server links to
     * @param link  - The link to the servers list
     */
    private static void readaLinksFromFile(ArrayList<String> links, String link) {
        try {
            BufferedReader readList = new BufferedReader(new FileReader(link));
            String line = "";

            while ((line = readList.readLine()) != null) {
                links.add(line);
            }

        } catch (FileNotFoundException e) {
            System.err.println("File not found, " + e);
        } catch (IOException e) {
            System.err.println("IOException, " + e);
        }

    }

    //Returns true if url is valid

    /**
     * The method check if its a valid url or not
     *
     * @param link -The link to chcek
     * @return true if its a valid url
     */
    private static boolean isValid(String link) {
        /* Try creating a valid URL */
        try {
            new URL(link).toURI();
            return true;
        }
        //If there was an Exception while creating URL object then its not a link
        catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if meta data file exist, if so calculate how much was already downloaded in bytes
     * Return 0 if its a new download
     *
     * @param fileName     - The name of the file
     * @param threadAmount - The number of threads to use
     * @return - Number of bytes downloaded
     */
    //Suppress the warning becaue we know we gonna get ArrayList<Range> from meta data file
    @SuppressWarnings("unchecked")
    private static long checkResumedDownload(String fileName, int threadAmount) {
        long totalByteRead = 0;

        try {
            //Check if file is exist
            if (new RandomAccessFile(fileName + ".Meta", "rw").length() != 0) {
                try {
                    //Get the Meta data array from the file
                    FileInputStream fileIn = new FileInputStream(fileName + ".Meta");
                    ObjectInputStream in = new ObjectInputStream(fileIn);
                    MetaDateRangesArray = (ArrayList) in.readObject();

                    //Calculate how much bytes was already downloaded
                    totalByteRead = calculatePrecentege();

                    in.close();
                    fileIn.close();
                } catch (Exception e) {
                    System.err.println(e);
                }
            }
        } catch (FileNotFoundException e) {
            System.err.println("File not found, " + e);
        } catch (IOException e) {
            System.err.println("IOException, " + e);
        }

        return totalByteRead;

    }

    /**
     * The method iterate over the array and get from each range how much bytes
     * was downloaded already
     *
     * @return - Number of bytes downloaded
     */
    private static long calculatePrecentege() {
        //Add the bytes from the first range (from 0)
        long ans = MetaDateRangesArray.get(0).getStart();

        //For each range calculate Bytes Read by subtract number of bytes that now in the start of range
        //with the end of the last range,which is also the start of this range in the begging.
        for (int i = 1; i < MetaDateRangesArray.size(); i++) {
            ans += MetaDateRangesArray.get(i).getStart() - MetaDateRangesArray.get(i - 1).getEnd();
        }

        return ans;
    }


}
