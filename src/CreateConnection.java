import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.*;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


public class CreateConnection {

    private static int TOOSMALL = 1000000; //Set too small file size
    static long sumOfBytes = 0; //Calculate if we pass 1%
    static long precentageDownloaded = 1;//Number of precentege downloaded
    static float onePrecentege = 0;//Calculate 1%
    static long contentLength = 0;//File length

    /**
     * @param links         - the connectin links
     * @param threadAmount  - number of threads
     * @param fileName      - the name of the file
     * @param totalByteRead - if its resume then how much bytes have reade
     */
    public static void startDownload(ArrayList<String> links, int threadAmount, String fileName, long totalByteRead) {
        //Create thread pool by number of thread given by user
        //1 by default
        Thread[] thDownload = new Thread[threadAmount];

        //List flag
        boolean list = false;

        //Resumed download flag
        boolean resume = false;
        boolean resumeDiffNum = false;

        try {
            //If we are using list of links file
            //Then set list flag to true
            if (links.size() > 1) {
                list = true;
            }

            //Open a new connection for headers
            URL url = new URL(links.get(0));
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");

            //Get content length
            contentLength = connection.getContentLengthLong();

            //Check what is 1 percent
            onePrecentege = (long) (contentLength * 0.01);

            //If resumed Download
            if (totalByteRead > 0) {
                precentageDownloaded = (totalByteRead * 100) / contentLength;
                resume = true;
            }

            //If the file or rest of the file is too small then use just 1 thread
            if (contentLength < TOOSMALL) {
                threadAmount = 1;
            }

            //Print number of using threads
            if (resume) {
                System.out.println("Downloading... ");
            } else if (threadAmount > 1) {
                System.out.println("Downloading using " + threadAmount + " connections...");
            } else {
                System.out.println("Downloading... ");
            }

            //Spread the content to ranges by number of threads
            long rangeSize = contentLength / threadAmount;

            //Spread the content to 1 less thread if range size is too small
            //Do it until range size for each thread is big enough
            while (rangeSize <= TOOSMALL) {
                rangeSize = contentLength / --threadAmount;
            }

            //Set lock object for managing threads resources
            final Lock lock = new ReentrantLock();

            //Initial chunksArray with ranges
            //If its Resumed with same threads number then just continue downloading
            if (resume) {
                //If its different amount of threads from before then adjust ranges
                if (threadAmount != IdcDm.MetaDateRangesArray.size()) {
                    IdcDm.ranges = calculateRangesForResumed(threadAmount, rangeSize);
                    calculateRanges();
                    //If its Resumed with bigger threads number then adjust ranges
                    if (threadAmount > IdcDm.MetaDateRangesArray.size()) {
                        IdcDm.MetaDateRangesArray = IdcDm.ranges;
                    } else {
                        resumeDiffNum = true;
                    }
                }
                //If its a new download
            } else {
                IdcDm.MetaDateRangesArray = calculateRangesForThreads(threadAmount, rangeSize);
            }

            //If its resume with bigger number of threads use createsConnectionByRange method for start them
            if (resumeDiffNum) {
                createsConnectionByRange(threadAmount, thDownload, list, links, url, lock, fileName);

            } else {
                //Set final variables fot threads used size
                boolean finalList = list;
                boolean finalResumeDiffNum = resumeDiffNum;

                //Create a new HttpConnection
                HttpURLConnection[] httpConnection = new HttpURLConnection[threadAmount];

                //Create all threads and connection and write to file
                for (int i = 0; i < threadAmount; i++) {

                    //Save final data for threads
                    final int finalI = i;


                    thDownload[i] = new Thread(() -> {
                        boolean isRangeIn = true;
                        Range range = IdcDm.MetaDateRangesArray.get(finalI);
                        if (range.getStart() >= range.getEnd()) {
                            isRangeIn = false;
                        }


                        try {
                            if (isRangeIn) {
                                //If its a list of links then give each thread a link
                                if (finalList) {
                                    //If its a list but no more links then use the first one
                                    if (finalI >= links.size()) {
                                        if (finalI - links.size() < links.size()) {
                                            //Print range for this thread
                                            printRanges(range, links, finalI - links.size());
                                            //open a new connection for each thread
                                            URL urlList = new URL(links.get(finalI - links.size()));
                                            httpConnection[finalI] = (HttpURLConnection) urlList.openConnection();
                                        } else {
                                            //Print range for this thread
                                            printRanges(range, links, 0);
                                            //open a new connection for each thread
                                            URL urlList = new URL(links.get(0));
                                            httpConnection[finalI] = (HttpURLConnection) urlList.openConnection();

                                        }
                                    } else {
                                        //Print range for this thread
                                        printRanges(range, links, finalI);
                                        //open a new connection for each thread
                                        URL urlList = new URL(links.get(finalI));
                                        httpConnection[finalI] = (HttpURLConnection) urlList.openConnection();
                                    }

                                } else {
                                    //Print range for this thread
                                    printRanges(range, links, 0);
                                    //open a new connection for each thread
                                    httpConnection[finalI] = (HttpURLConnection) url.openConnection();
                                }

                                //Get from http request start to end of range
                                httpConnection[finalI].setRequestProperty("Range", "bytes=" + range.getStart() + "-"
                                        + range.getEnd() + "");
                                //Set timeout to 30 seconds
                                httpConnection[finalI].setReadTimeout(30000);
                                //Start connection
                                httpConnection[finalI].connect();

                                //Create read & write object for this thread
                                writeToFile writeToFile = new writeToFile(lock, httpConnection[finalI], fileName, finalI,
                                        range, finalResumeDiffNum);
                                writeToFile.run();
                            }
                        } catch (MalformedURLException e) {
                            System.err.println("URL link is invalid, " + e);
                        } catch (IOException e) {
                            System.err.println("Download failed due to connection timeout, " + e);
                        }


                    });

                    //Start this thread
                    thDownload[i].start();
                }
            }

        } catch (ProtocolException e) {
            System.err.println("there is an error in the underlying protocol, " + e);
        } catch (MalformedURLException e) {
            System.err.println("URL link is invalid, " + e);
        } catch (IOException e) {
            System.err.println("Download failed due to connection timeout, " + e);
        } finally {
            //Wait for all the threads to finish
            for (Thread th : thDownload) {
                try {
                    th.join();
                } catch (InterruptedException e) {
                    System.err.println(th.getName() + " Thread has been interrupted, " + e);

                }
            }
            try {
                //If at the enf the file length equal to content length then we succeeded
                if (new RandomAccessFile(fileName, "rw").length() == contentLength && precentageDownloaded >= 97) {

                    System.out.println("Download succeeded");

                    //Delete metaDate file
                    new File(fileName + ".Meta").delete();

                } else {
                    System.out.println("Download failed");

                }
            } catch (IOException e) {
                System.err.println("Download failed due to connection timeout, " + e);
            }
        }

    }

    /**
     * The method gets a range and a link and print it to the user
     * The methos show on which range this thread are working
     *
     * @param range - Start and end to print
     * @param links - List of server links (Length is 1 if there is no list, just 1 link)
     * @param index - The index to take from the list (0 if its just 1 server link)
     */
    private static void printRanges(Range range, ArrayList<String> links, int index) {
        //Print link and ranges for this thread
        System.out.println(Thread.currentThread().getName() +
                " Start downloading range (" + range.getStart() + " - " + range.getEnd()
                + ") from:\n" + links.get(index));
    }

    /**
     * The method adjust threads to work on each range in the meta data array
     * It creates the working thread and continue the download
     *
     * @param threadAmount - Number og threads
     * @param thDownload   - Thread pool (array of threads)
     * @param list         - This is a list of server links or not
     * @param links        - The server links list (Length is 1 if its not a list)
     * @param url          - The url from which it should be downloading
     * @param lock         - The lock object for locking shred resources
     * @param fileName     - The name of the file to download
     */
    private static void createsConnectionByRange(int threadAmount, Thread[] thDownload, boolean list,
                                                 ArrayList<String> links, URL url, Lock lock, String fileName) {
        //check if we metaData size not divisible by number of thread
        boolean divisible = false;
        //Get the number of range for each thread to work on
        int length = (int) Math.ceil(IdcDm.MetaDateRangesArray.size() / (threadAmount + 0.0));
        if (length * threadAmount != IdcDm.MetaDateRangesArray.size()) {
            divisible = true;
        }
        //Calculate for each thread on which ranges from meta data is working
        int index = 0;

        //Create a new HttpConnection
        HttpURLConnection[] httpConnection = new HttpURLConnection[threadAmount];

        //Create all threads and connection and write to file
        for (int i = 0; i < threadAmount; i++) {
            //If divisible and this is the last thread then give it less to work on
            if (divisible && i == threadAmount - 1) {
                length = IdcDm.MetaDateRangesArray.size() - index;
            }

            //Save final data for threads
            final int finalI = i;
            int finalIndex = index;
            int finalLength = length;
            thDownload[i] = new Thread(() -> {

                for (int j = 0; j < finalLength; j++) {
                    //If we passed meta data size then break the loop
                    if (finalIndex + j >= IdcDm.MetaDateRangesArray.size()) break;
                    boolean isRangeIn = true;
                    Range rangeLess = IdcDm.MetaDateRangesArray.get(finalIndex + j);
                    if (rangeLess.getStart() >= rangeLess.getEnd()) {
                        isRangeIn = false;
                    }


                    try {
                        //If its a list of links then give each thread a link
                        if (list) {
                            //If its a list but no more links then use the first one
                            if (finalI >= links.size()) {
                                if (finalI - links.size() < links.size()) {
                                    //open a new connection for each thread
                                    URL urlList = new URL(links.get(finalI - links.size()));
                                    httpConnection[finalI] = (HttpURLConnection) urlList.openConnection();
                                } else {
                                    //open a new connection for each thread
                                    URL urlList = new URL(links.get(0));
                                    httpConnection[finalI] = (HttpURLConnection) urlList.openConnection();
                                }
                            }
                        } else {
                            //open a new connection for each thread
                            httpConnection[finalI] = (HttpURLConnection) url.openConnection();
                        }
                        if (isRangeIn) {

                            httpConnection[finalI].setRequestProperty("Range", "bytes=" + rangeLess.getStart() +
                                    "-" + rangeLess.getEnd() + "");
                            httpConnection[finalI].setReadTimeout(30000); //set timeout to 30 seconds
                            httpConnection[finalI].connect();

                            //Create read & write object for this thread
                            writeToFile writeToFile = new writeToFile(lock, httpConnection[finalI], fileName,
                                    finalIndex + j, rangeLess, true);
                            writeToFile.run();
                        }
                    } catch (MalformedURLException e) {
                        System.err.println("URL link is invalid, " + e);
                    } catch (IOException e) {
                        System.err.println("Download failed due to connection timeout, " + e);
                    }
                }
            });
            index += length;

            //Start this thread
            thDownload[i].start();
        }

    }

    /**
     * This method get the range list after meta data adjust
     * for each thread who did not get range, give it a range from another range
     */
    private static void calculateRanges() {
        if (IdcDm.ranges.get(0).getStart() == -1) {
            if (IdcDm.ranges.get(1).getStart() != -1) {
                long length = IdcDm.ranges.get(1).getEnd() - IdcDm.ranges.get(1).getStart();
                IdcDm.ranges.get(0).setStart(IdcDm.ranges.get(1).getStart());
                IdcDm.ranges.get(0).setEnd(IdcDm.ranges.get(0).getStart() + (length / 2));
                IdcDm.ranges.get(1).setStart(IdcDm.ranges.get(0).getEnd());
            }
        }
        for (int i = 1; i < IdcDm.ranges.size(); i++) {
            if (IdcDm.ranges.get(i).getStart() == -1) {
                if (IdcDm.ranges.get(i - 1).getStart() != -1) {
                    long length = IdcDm.ranges.get(i - 1).getEnd() - IdcDm.ranges.get(i - 1).getStart();
                    IdcDm.ranges.get(i).setEnd(IdcDm.ranges.get(i - 1).getEnd());
                    IdcDm.ranges.get(i - 1).setEnd(IdcDm.ranges.get(i - 1).getStart() + (length / 2));
                    IdcDm.ranges.get(i).setStart(IdcDm.ranges.get(i - 1).getEnd());
                }
            }
        }
    }

    /**
     * This methed calculate ranges for threads base on file length and number of threads
     *
     * @param threadAmount - Number of threads
     * @param rangeSize    - Range size for each range
     * @return - ArrayList<Range> with range for each thread
     */
    private static ArrayList<Range> calculateRangesForThreads(int threadAmount, long rangeSize) {
        ArrayList<Range> ranges = new ArrayList<>();

        //Calculate start and end range for each thread
        for (int i = 0; i < threadAmount; i++) {

            long start = (rangeSize * i);
            long end = (rangeSize * (i + 1));

            //If its the last thread, take all byte that left
            if (i == threadAmount - 1) {
                end += contentLength - end;
            }
            Range range = new Range(start, end);
            ranges.add(range);

        }
        return ranges;
    }

    /**
     * This methed calculate ranges for threads base on meta data file and the last download
     *
     * @param threadAmount - Number of thread to use
     * @param rangeSize    - Range size for each thread
     * @return ArrayList with ranges
     */
    private static ArrayList<Range> calculateRangesForResumed(int threadAmount, long rangeSize) {
        ArrayList<Range> ranges = calculateRangesForThreads(threadAmount, rangeSize);

        for (int i = 0; i < threadAmount; i++) {
            long start = ranges.get(i).getStart();
            long end = ranges.get(i).getEnd();
            long min = end;
            long max = start;
            boolean notInRange = false;

            for (Range range : IdcDm.MetaDateRangesArray) {
                //Check if this Range is between start to end
                if ((start <= range.getStart()) && (end >= range.getStart())) {
                    if (range.getStart() < min) {
                        min = range.getStart();
                        notInRange = true;
                    }
                    if (range.getEnd() > max) {
                        max = range.getEnd();
                        notInRange = true;
                    }

                }
            }
            if (notInRange) ranges.set(i, new Range(min, max));
            else ranges.set(i, new Range(-1, -1));
        }
        return ranges;
    }

}