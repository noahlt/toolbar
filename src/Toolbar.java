package org.noahtye.toolbar;

import org.apache.commons.exec.*;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.font.FontRenderContext;

import java.util.Random;
import java.util.ArrayList;
import java.util.List;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.Charset;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.ByteArrayOutputStream;


class Tool {
    final public String label;
    final public String command;

    public Tool(String label, String command) {
        this.label = label;
        this.command = command;
    }
}

class MouseHandler extends MouseAdapter {

    final private Toolbar toolbar;

    public MouseHandler(Toolbar t) {
        toolbar = t;
    }

    public void mousePressed(MouseEvent e) {
    }

    public void mouseReleased(MouseEvent e) {
        // this is crappy, we should just have Toolbar implement the listeners
        toolbar.mouseUp(e.getX(), e.getY());
    }
}

class ResultHandler implements ExecuteResultHandler {
    final private OutputStream os;
    final private Graphics g;

    public ResultHandler(OutputStream outputStream, Graphics graphics) {
        os = outputStream;
        g = graphics;
    }

    public void onProcessComplete(int exitCode) {
        System.out.println("complete!");
    }

    public void onProcessFailed(ExecuteException e) {
        System.out.println("exec failed: " + e.getMessage());
    }
}

class TextBlock {
    private final ArrayList<String> lines;

    public TextBlock() {
        lines = new ArrayList<String>();
        lines.add("");
    }

    public void appendChar(char c) {
        if (c == '\n') {
            lines.add("");
        } else {
            final int lastLine = lines.size() - 1;
            lines.set(lastLine, lines.get(lastLine) + c);
        }
    }

    public void appendByte(byte b) {
        appendChar((char) b);
    }

    public List<String> getLastLines(int n) {
        return lines.subList(Math.max(0, lines.size() - n), lines.size()-1);
    }
}

class Displayer implements Runnable {
    // this should not be a constant, this should react to the window size.
    private static final int LINES_DISPLAYED_AT_ONCE = 24;
    // this is just a guess. no idea how to pick the right number.
    private static final int BUFFER_SIZE = 1024;

    private final InputStream is;
    private final OutputStream os;
    private final Graphics g;

    public Displayer(InputStream is, Graphics g) {
        this.is = is;
        this.g = g;
        this.os = new ByteArrayOutputStream();
    }

    public void run() {
        final byte[] buffer = new byte[BUFFER_SIZE];
        int length;
        try {
            // This is super gross and hacky and certainly inefficient, but I wrote it
            // quickly, and that's what counts.
            final TextBlock text = new TextBlock();
            List<String> lines;
            // reminder: this works because is.read() is a blocking call
            while ((length = is.read(buffer)) > 0) {
                for (int i = 0; i < length; i++) {
                    // in particular, appending one byte at a time seems questionable:
                    text.appendByte(buffer[i]);
                }

                // clear output area
                // (output area really needs to be abstracted to a view class)
                g.setColor(new Color(250, 250, 250));
                g.fillRect(Toolbar.TOOLBAR_WIDTH, 0, Toolbar.WIDTH, Toolbar.HEIGHT);

                // draw text to output area
                g.setColor(new Color(59, 74, 81));
                g.setFont(new Font("Monaco", Font.PLAIN, 10));
                lines = text.getLastLines(LINES_DISPLAYED_AT_ONCE);
                for (int i = 0; i < lines.size(); i++) {
                    g.drawString(lines.get(i), Toolbar.TOOLBAR_WIDTH + Toolbar.MARGIN,
                            Toolbar.HACK_TITLEBAR_HEIGHT + 13*(i+1));
                }
            }
        } catch (final Exception e) {
            System.out.println("Displayer error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

class StreamHandler implements ExecuteStreamHandler {
    final private OutputStream out;
    final private Graphics g;

    private Thread thread;

    public StreamHandler(OutputStream out, Graphics g) {
        this.out = out;
        this.g = g;
    }

    public void setProcessErrorStream(InputStream is) {}
    public void setProcessInputStream(OutputStream os) {}
    public void setProcessOutputStream(InputStream is) {
        if (out == null) return;
        thread = new Thread(new Displayer(is, g));
    }

    public void start() {
        thread.start();
    }

    public void stop() {
        try {
            // stop() is called by executeInternal(), which is called in the background
            // thread that runs the process when we call execute().  So it's okay to
            // block here.
            //
            // We need to block this background thread to prevent the process object from
            // being garbage collected.  If the process object gets gc'd, it closes all
            // the attached streams.  This causes a race condition between our thread that
            // consumes the process' output stream and exec's thread that runs the process:
            // if the exec thread closes the streams before our consumer thread can finish
            // reading the output from the stream, our consumer thread will get a "stream
            // closed" IOException.
            //
            // We avoid this error by blocking the exec thread on the consumer thread,
            // with this line:
            thread.join();
            // Debugging this was greatly aided by this old newsgroup post:
            // https://groups.google.com/forum/#!topic/comp.lang.java.programmer/03DxQMnyI6M
        } catch (InterruptedException e) {
            // we just lose the stream output
        }
    }
}

public class Toolbar extends ApplicationFrame {
    // So apparently the drawing context is created at the size of the
    // entire window, that is, including the titlebar.  This means that
    // if you draw at the very top of the graphics context, you draw
    // underneath the title bar.  On my machine the title bar is 22px
    // tall, so this is the hack we have to add to every Y-coordinate.
    public static int HACK_TITLEBAR_HEIGHT = 22;

    public static int MARGIN = 10;

    public static int WIDTH = 600;
    public static int HEIGHT = 324; // FIXME deal with resizing window
    public static int TOOLBAR_WIDTH = 100; // FIXME measure text & make toolbar width based on that

    public ArrayList<Tool> tools = new ArrayList<Tool>();

    private int lineHeight = 0;

    private InputStream consoleStream;

    public static void main(String[] args) {
        Toolbar t = new Toolbar();
        t.center();
        t.setVisible(true);
    }

    public Toolbar() {
        setTitle("Toolbar");
        setSize(WIDTH, HEIGHT);
        setResizable(false); // FIXME window should be resizable
        addMouseListener(new MouseHandler(this));

        try {
            final Charset charset = Charset.forName("US-ASCII");
            final BufferedReader br = Files.newBufferedReader(Paths.get(System.getProperty("user.home"), ".toolbar"), charset);
            String line;
            while ((line = br.readLine()) != null) {
                final int colonPosition = line.indexOf(':');
                if (colonPosition >= 0) {
                    tools.add(new Tool(line.substring(0, colonPosition),
                                       line.substring(colonPosition+1)));
                }
            }
        } catch (IOException e) {
            System.out.println("unable to read .toolbar config file");
            e.printStackTrace();
        }
    }

    public void paint(Graphics graphics) {
        final Graphics2D g = (Graphics2D) graphics;
        // Draw toolbar
        g.setColor(new Color(210, 217, 221));
        g.fillRect(0, 0, TOOLBAR_WIDTH, HEIGHT);
        // draw output area
        g.setColor(new Color(250, 250, 250));
        g.fillRect(TOOLBAR_WIDTH, 0, WIDTH, HEIGHT);

        g.setColor(new Color(51, 60, 67));
        g.setFont(new Font("Monaco", Font.PLAIN, 10));

        // Maybe save this somewhere else?
        // Note also that the first call to this is very slow (~500ms) but subsequent calls are fast,
        // so maybe there's a better place to calculate it, too.
        lineHeight = g.getFontMetrics().getHeight();

        for (int i = 0; i < tools.size(); i++) {
            final Tool tool = tools.get(i);
            g.drawString(tool.label, MARGIN, HACK_TITLEBAR_HEIGHT + (i + 1) * lineHeight);
        }

        // draw development label
        g.fillRect(0, HEIGHT-50, 100, 20);
        g.setColor(new Color(250, 250, 250));
        g.drawString("development", MARGIN, HEIGHT-50+lineHeight);
    }

    public void mouseUp(int x, int y) {
        if (x > TOOLBAR_WIDTH) return; // clicked outside the toolbar
        // wish there was a good way to express this algebraic layout constraint so that I could
        // tell the computer "i know the layout, give me the item" or vice versa instead of
        // duplicating this code.
        final int i = (y - HACK_TITLEBAR_HEIGHT) / lineHeight;
        if (i >= tools.size()) return; // clicked on empty space in the toolbar
        final String cmd = tools.get(i).command;
        System.out.println("exec: " + cmd);
        try {
            // clear screen
            Graphics g = getGraphics();
            g.setColor(new Color(250, 250, 250));
            g.fillRect(TOOLBAR_WIDTH, 0, WIDTH, HEIGHT);
            // start process
            OutputStream os = new ByteArrayOutputStream();
            CommandLine cmdLine = CommandLine.parse(cmd);
            DefaultExecutor executor = new DefaultExecutor();
            ExecuteResultHandler resultHandler = new ResultHandler(os, getGraphics());
            executor.setStreamHandler(new StreamHandler(os, getGraphics()));
            executor.execute(cmdLine, resultHandler);
        } catch (IOException e) {
            System.out.println("error executing " + cmd);
        }
    }
}
