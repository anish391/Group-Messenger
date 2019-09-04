package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {

    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    static final int SERVER_PORT = 10000;
    private static final String keyField = "key";
    private static final String valueField = "value";
    private Uri uri;
    static String portArray[] = {"11108","11112","11116","11120","11124"};
    String myPort;
    static int sequenceNumber = 0;
    int proposal = 0;
    int agreement;
    ArrayList<Integer> proposals = new ArrayList<Integer>();
    ArrayList<Message> messageQueue = new ArrayList<Message>();
    PriorityQueue<Message> queue = new PriorityQueue<Message>();
    LinkedList<Message> list = new LinkedList<Message>();
    Set<String> set = new HashSet<String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());

        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));

        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */

        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        myPort = String.valueOf((Integer.parseInt(portStr) * 2));

        //Log.v(TAG, myPort);

        try{
            Log.v(TAG,"Server Socket creation");
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        findViewById(R.id.button4).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                EditText editText = (EditText) findViewById(R.id.editText1);
                String msg = editText.getText().toString();
                if (msg != null && !msg.isEmpty()) {
                    editText.setText("");
                    //Log.v(TAG, "Message button working.");
                    new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort);
                }
            }
        });
    }


    /*
     * TODO: The below code for ServerTask and ClientTask classes is reused from PA1 and PA2A with changes.
     */

    class Message implements Comparable<Message>{
        String message;
        boolean deliverable = false;
        int port;
        int sequence;

        /*
        * A message class is created which will be used alongside priority queue
        * to mimic the hold-back queue used in ISIS algorithm. The constructor takes
        * the message string as well as the proposed/agreed sequence values.
        * https://stackoverflow.com/questions/45973532/priorityqueues-custom-classes-and-the-comparable-interface
        */

        public Message(String message, int sequence, int port){
            this.message = message;
            this.sequence = sequence;
            this.port = port;
        }

        /*
        * Overriding compareTo method to add Message objects to the priority queue on the basis of
        * proposed sequence numbers.
        */
        @Override
        public int compareTo(Message another) {
            if(sequence < another.sequence)
                return -1;
            else if(sequence > another.sequence)
                return 1;
            else{
                if(port < another.port)
                    return -1;
                else
                    return 1;
            }
        }
    }

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            String ip;
            //Log.v(TAG,"Server Socket Created");
            try {
                while(true) {
                    Socket clientSocket = serverSocket.accept();
                    //Thread.sleep(50);
                    //Log.v(TAG,"Proposal: " + Integer.toString(proposal));
                    DataInputStream in = new DataInputStream(clientSocket.getInputStream());
                    DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());
                    ip = in.readUTF();
                    String[] clientContent = ip.split("<><");
                    //Log.v(TAG,"Message size: "+clientContent.length);

                    /*
                    * The if statement gets input message from the Client side and sends back proposal
                    * to the Client Side. It also updates the local proposed sequence value.
                    */
                    //TODO: Isis algorithm as written by you.
                    if(clientContent.length==2) {
                        out.writeUTF(Integer.toString(proposal));
                        String msg = clientContent[0];
                        int port = Integer.parseInt(clientContent[1]);
                        proposal = proposal + 1;
                        out.flush();
                    }

                    /*
                    * The else statement gets the maximum agreed number from the Client side
                    * and adds the message and sequence to the Message object to add in Priority Queue.
                    * The proposal value of the current process is added with a fraction of the
                    * port number to avoid clash of sequence numbers. The messages are stored as objects
                    * of the class Message in the queue in increasing order of proposed sequence.
                    */

                    else{
                        String msg = clientContent[0];
                        int sequence = Integer.parseInt(clientContent[1]);
                        int port = Integer.parseInt(clientContent[2]);

                        //Log.v(TAG,"Agreement: "+clientContent[1]);
                        //Log.v(TAG,"Port:"+clientContent[2]);
                        //if(agreement>=proposal){
                        //    proposal = agreement + 1 ;
                        //}
                        //Log.v(TAG,"Proposed_sequence: "+proposal);
                        if(sequence>proposal){
                            proposal = sequence;

                        }
                        Message m = new Message(clientContent[0], sequence, port);
                        queue.add(m);


                    }
                    while(queue.peek()!=null){
                        Message entry = queue.poll();
                        Log.v(TAG,"Queue head: "+entry.message);
                        Log.v(TAG,"Sequence Number:"+entry.sequence);
                        publishProgress(entry.message, Integer.toString(entry.sequence),Integer.toString(entry.port));
                    }

                    /*
                    * If there are messages in the queue, publish the message to all
                    * clients by polling the priority queue.
                    * */


                    //Log.v(TAG,"Message to be delivered: "+ip);

                    in.close();
                    out.close();
                    clientSocket.close();
                    //Log.v(TAG,"Server still running.");
                }
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG,"Server error.");
            } /*catch (InterruptedException e) {
                e.printStackTrace();
            }*/
            return null;
        }

        protected void onProgressUpdate(String...strings) {
            //super.onProgressUpdate(strings);
            String strReceived = strings[0].trim();
            String keyValue = strings[1];
            String port = strings[2];
            /*
             * References:
             * 1) https://developer.android.com/reference/android/content/ContentResolver
             * 2) https://developer.android.com/reference/android/net/Uri.html
             * 3) https://developer.android.com/reference/android/content/ContentValues
             *
             * The ContentResolver object is used to interact with the Content provider in order to perform insert and query operations.
             * It receives a ContentResolver object from getContentResolver() which is a method of the Context class.
             * The ContentValues object is used to store values which are to be processed by the ContentResolver.
             * The URI object is used to uniquely identify and interact with a particular ContentProvider
             */
            Log.v(TAG,"Running publish progress");
            ContentResolver contentResolver = getContentResolver();
            Uri.Builder uriBuilder = new Uri.Builder();
            uriBuilder.authority("edu.buffalo.cse.cse486586.groupmessenger2.provider");
            uriBuilder.scheme("content");
            uri = uriBuilder.build();
            ContentValues contentValues = new ContentValues();
            contentValues.put(keyField, Integer.toString(sequenceNumber));
            contentValues.put(valueField, strReceived);
            contentResolver.insert(uri, contentValues);
            TextView textView = (TextView) findViewById(R.id.textView1);
            textView.append("Port No: "+port+" Sequence: "+sequenceNumber+"\n"+keyValue + ": " + strReceived + "\n");
            sequenceNumber += 1;
        }
    }

    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {

            String message = msgs[0];
            String myPort = msgs[1];
            Socket socket = null;
            for(String port: portArray) {
                try {
                    socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(port));
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    DataInputStream in = new DataInputStream(socket.getInputStream());

                    /*
                    * The snippet to share "ACK" from server side to prevent client socket from closing
                    * prematurely has been removed since the need for that rose from variables defined
                    * inside the scope of try statement. The client no longer needs "ACK" from server. :)
                    * */
                    out.writeUTF(message+"<><"+port);
                    out.flush();
                    int sequence = Integer.parseInt(in.readUTF());
                    // Storing the proposed sequences from other AVDs in an array in order to find max sequence for the current message between all AVDs.
                    proposals.add(sequence);
                    Log.v(TAG, "Proposals: " + proposals);
                    socket.close();
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                    //socketArray.remove(socket);
                    Log.e(TAG,"AVD crashed at port: "+port);
                    //proposals.remove(port);
                    Log.v(TAG,"1st for loop failed.");
                    //portArray.remove(port);
                    //continue;
                }
            }
            //Max agreed proposed value between all processes.
            agreement = Collections.max(proposals);
            Log.v(TAG,"Starting second for loop.");
            for(String port: portArray){
                try{
                    socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(port));
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    Log.v(TAG,"Sending agreement to server.");
                    out.writeUTF(message+"<><"+agreement+"<><"+port);
                    Log.v(TAG,"Agreement sent succesfully");
                    out.flush();
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    String failedPort = port;
                    //proposals.remove(port);
                    Log.v(TAG,"2nd for Loop failed");
                    Log.e(TAG,"AVD crashed at port: "+port);
                    e.printStackTrace();
                }
            }
            Log.v(TAG,"Agreement on client side:"+agreement);
            proposals.clear(); // Clear list of proposals from all AVDs so that size of the list remains equal to number of AVDs.
            return null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }
}


