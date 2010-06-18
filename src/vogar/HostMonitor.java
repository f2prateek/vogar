/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package vogar;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.Socket;
import java.util.Collections;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Connects to a target process to monitor its action.
 */
class HostMonitor {
    /** Sometimes we fail to parse XML documents; echo up to this many bytes back to the user when that happens. */
    private static final int BAD_XML_SNIPPET_SIZE = 1024;

    private final long monitorTimeoutSeconds;

    HostMonitor(long monitorTimeoutSeconds) {
        this.monitorTimeoutSeconds = monitorTimeoutSeconds;
    }

    /**
     * Connect to the target process on the given port, read all of its
     * outcomes into {@code handler}, and disconnect.
     */
    public boolean monitor(int port, Handler handler) {
        Socket socket;
        InputStream in;
        int attempt = 0;
        do {
            try {
                socket = new Socket("localhost", port);
                in = new BufferedInputStream(socket.getInputStream());
                if (checkStream(in)) {
                    in.mark(BAD_XML_SNIPPET_SIZE);
                    break;
                }
                in.close();
                socket.close();
            } catch (ConnectException recoverable) {
            } catch (IOException e) {
                Console.getInstance().info("Failed to connect to localhost:" + port, e);
                return false;
            }

            if (attempt++ == monitorTimeoutSeconds) {
                Console.getInstance().info("Exceeded " + monitorTimeoutSeconds
                        + " attempts to connect to localhost:" + port);
                return false;
            }

            Console.getInstance().verbose("connection " + attempt + " to localhost:" + port
                    + " failed; retrying in 1s");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }
        } while (true);

        Console.getInstance().verbose("action monitor connected to " + socket.getRemoteSocketAddress());

        try {
            SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
            InputSource inputSource = new InputSource(in);
            inputSource.setEncoding("UTF-8");
            parser.parse(inputSource, new ClientXmlHandler(handler));
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            Console.getInstance().verbose("connection error from localhost:" + port + " " + e);
            return false;
        } catch (SAXException e) {
            try {
                in.reset();
                byte[] offendingXml = new byte[BAD_XML_SNIPPET_SIZE];
                int bytes = in.available() > 0 ? in.read(offendingXml) : 0;
                Console.getInstance().warn("received bad XML from localhost:" + port
                        + " " + new String(offendingXml, 0, bytes, "UTF-8"));
            } catch (IOException another) {
                Console.getInstance().warn("received bad XML from localhost:" + port + " " + e);
            }
            return false;
        }

        try {
            socket.close();
        } catch (IOException ignored) {
        }

        return true;
    }

    /**
     * Somewhere between the host and client process, broken socket connections
     * are being accepted. Before we try to do any work on such a connection,
     * check it to make sure it's not dead!
     *
     * TODO: file a bug (against adb?) for this
     */
    private boolean checkStream(InputStream in) throws IOException {
        in.mark(1);
        if (in.read() == -1) {
            return false;
        } else {
            in.reset();
            return true;
        }
    }

    /**
     * Handles updates on the outcomes of a target process.
     */
    public interface Handler {
      
        /**
         * @param runnerClass can be null, indicating nothing is actually being run. This will
         *        happen in the event of an impending error.
         */
        void runnerClass(String runnerClass);

        /**
         * Receive a completed outcome.
         */
        void outcome(Outcome outcome);

        /**
         * Receive partial output from an action being executed.
         */
        void output(String outcomeName, String output);
    }

    class ClientXmlHandler extends DefaultHandler {
        private final Handler handler;

        private String currentRunnerType;
        private String currentOutcomeName;
        private String currentActionName;
        private Result currentResult;
        private StringBuilder output = new StringBuilder();

        ClientXmlHandler(Handler handler) {
            this.handler = handler;
        }

        /*
         * Our XML wire format looks like this:
         *
         * <?xml version='1.0' encoding='UTF-8' ?>
         * <vogar-monitor>
         *   <outcome name="java.util.FormatterTest" action="java.util.FormatterTest" 
         *            runner="vogar.target.JUnitRunner">
         *     test output
         *     more test output
         *     <result value="SUCCESS" />
         *   </outcome>
         * </vogar-monitor>
         */

        @Override public void startElement(String uri, String localName,
                String qName, Attributes attributes) throws SAXException {
            if (qName.equals("outcome")) {
                if (currentOutcomeName != null) {
                    throw new IllegalStateException();
                }

                currentOutcomeName = attributes.getValue("name");
                currentActionName = attributes.getValue("action");

                handler.output(currentOutcomeName, "");
                handler.runnerClass(attributes.getValue("runner"));
                return;

            } else if (qName.equals("result")) {
                currentResult = Result.valueOf(attributes.getValue("value"));
                return;

            } else if (!qName.equals("vogar-monitor")) {
                throw new IllegalArgumentException("Unrecognized: " + qName);
            }
        }

        @Override public void characters(char[] ch, int start, int length)
                throws SAXException {
            if (currentOutcomeName != null) {
                String text = new String(ch, start, length);
                output.append(text);
                handler.output(currentOutcomeName, text);
            }
        }

        @Override public void endElement(String uri, String localName, String qName)
                throws SAXException {
            if (qName.equals("outcome")) {
                handler.outcome(new Outcome(currentOutcomeName, currentActionName,
                        currentResult, Collections.singletonList(output.toString())));
                currentOutcomeName = null;
                currentActionName = null;
                currentResult = null;
                output.delete(0, output.length());
            }
        }
    }
}
