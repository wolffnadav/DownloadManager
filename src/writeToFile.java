import java.io.*;
import java.net.*;
import java.util.concurrent.locks.Lock;


public class writeToFile {

    private HttpURLConnection httpConnection;//httpConnection to download data
    private String fileName;//name of the file to download
    final private Lock lock;//lock object for threads
    private long seekStart;//index to write to
    private int threadNum;//this thread index
    private boolean resume;//is this resume download or not
    private Range range;//the range to download


    /**
     * writeToFile constructor
     *
     * @param lock           - lock object for threads
     * @param httpConnection - httpConnection to download data
     * @param fileName       - name of the file to download
     * @param threadNum      - this thread index
     * @param range          - the range to download
     * @param resume         - is this resume download or not
     */
    writeToFile(Lock lock, HttpURLConnection httpConnection, String fileName, int threadNum, Range range, boolean resume) {

        this.httpConnection = httpConnection;
        this.fileName = fileName;
        this.seekStart = range.getStart();
        this.lock = lock;
        this.threadNum = threadNum;
        this.range = range;
        this.resume = resume;

    }

    /**
     * This method download the input from http connection and write it to the file
     * based on seekStart (seek to write to)
     */
    public synchronized void run() {
        //If its the start of the download print 0 percent
        if (this.range.getStart() == 0) {
            System.out.println("Downloaded 0%");
        }

        //initialize write variables
        int bytesRead;
        byte[] buffer = new byte[IdcDm.CHUNKSIZE];

        try {

            //Opens input stream from the HTTP connection
            InputStream readerInput = httpConnection.getInputStream();

            //Opens an RandomAccessFile stream to write into file
            RandomAccessFile writer = new RandomAccessFile(this.fileName, "rw");

            //If its resume download or other thread then start one,
            //then seek to where we start is
            if (this.range.getStart() != 0) {
                writer.seek(this.seekStart);
            }


            //Write to file and to fileMeta
            while ((bytesRead = readerInput.read(buffer)) != -1) {
                writer.write(buffer, 0, bytesRead);
                this.seekStart += bytesRead;//Add to start the length read

                //Only one thread can enter at a time
                //Save to meta data file
                lock.lock();
                writeToMeta(this.fileName, range, threadNum, this.seekStart);
                lock.unlock();


                //Only one thread can enter at a time
                lock.lock();
                CreateConnection.sumOfBytes += bytesRead;//

                //If we wrote more then 1% print a message and remove the last content
                if (CreateConnection.sumOfBytes >= CreateConnection.onePrecentege) {

                    System.out.println("Downloaded " + CreateConnection.precentageDownloaded + "%");
                    CreateConnection.precentageDownloaded++;

                    CreateConnection.sumOfBytes -= CreateConnection.onePrecentege;
                }

                lock.unlock();

            }

            readerInput.close();

        } catch (FileNotFoundException e) {
            System.err.println("File not found, " + e);
        } catch (IOException e) {
            System.err.println("Download failed due to connection timeout, " + e);
            writeToMeta(this.fileName, range, threadNum, this.seekStart);
        }
    }

    /**
     * This method update meta data file
     *
     * @param fileName  - Name of the file
     * @param range     - Range to update
     * @param index     - Index to update in meta data array
     * @param seekStart - The new value to update
     */
    private void writeToMeta(String fileName, Range range, int index, long seekStart) {

        try {
            //Create meta data file
            FileOutputStream fileOutMeta = new FileOutputStream(fileName + ".Temp");
            ObjectOutputStream outMeta = new ObjectOutputStream(fileOutMeta);
            outMeta.flush();
            outMeta.reset();
            //Update range and meta data array
            range.setStart(seekStart);
            IdcDm.MetaDateRangesArray.set(index, range);
            //Save it in temp file
            outMeta.writeObject(IdcDm.MetaDateRangesArray);
            //Rename file to .Meta
            new File(fileName + ".Temp").renameTo(new File(fileName + ".Meta"));

            //close all streams
            outMeta.close();
        } catch (IOException e) {
            System.err.println("IOException, " + e);
        }

    }

}

