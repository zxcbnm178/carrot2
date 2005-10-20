package com.dawidweiss.carrot2.browser;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;

import org.jdesktop.jdic.browser.WebBrowser;

import com.dawidweiss.carrot.core.local.clustering.RawDocument;
import com.dawidweiss.carrot.core.local.impl.ClustersConsumerOutputComponent;
import com.dawidweiss.carrot.core.local.impl.ClustersConsumerOutputComponent.Result;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.uif_lite.panel.SimpleInternalFrame;

/**
 * A component that displays results of a clustering process.
 *  
 * @author Dawid Weiss
 */
public class ResultsTab extends JPanel {
    private final static String MAIN_CARD = "main";
    private final static String PROGRESS_CARD = "progress";

    private String query;
    private String processId;
    private WebBrowser browserView;
    private RawClustersTree clustersTree;
    private Result result;
    private JPanel cards;
    private JLabel progressInfo;
    
    private class SelfRemoveAction implements ActionListener {
        public void actionPerformed(ActionEvent actionEvent) {
            // find the tabbedpane we belong to.
            Component last = ResultsTab.this;
            while (last != null)
            {
                if (last.getParent() instanceof JTabbedPane)
                {
                    ((JTabbedPane) last.getParent()).remove(last);
                    break;
                }
                last = last.getParent();
            }        
            
        }
    }

    public ResultsTab(String query, String processId) {
        this.query = query;
        this.processId = processId;

        buildSplit();
    }
    
    private void buildSplit() {
        this.setLayout(new BorderLayout());

        SimpleInternalFrame all = new SimpleInternalFrame("Query: "
                + ("".equals(query) ? "<empty>" : query)
                + " (process: " + processId + ")");
        JToolBar toolbar = new JToolBar();
        ToolbarButton closeButton = new ToolbarButton(
                new ImageIcon(this.getClass().getResource("remove.gif")),
                new ImageIcon(this.getClass().getResource("remove_co.gif")));
        closeButton.addActionListener(new SelfRemoveAction());
        closeButton.setToolTipText("Close this tab");
        ToolbarButton homeButton = new ToolbarButton(
                new ImageIcon(this.getClass().getResource("home_nav.gif")),
                new ImageIcon(this.getClass().getResource("home_nav_dis.gif")));
        homeButton.setToolTipText("Display all documents (from all clusters)");
        homeButton.addActionListener(
                new ActionListener() {
                    public void actionPerformed(ActionEvent arg0) {
                        showAllDocuments();
                    }
                });
        toolbar.add(homeButton);
        toolbar.add(closeButton);
        all.setToolBar(toolbar);

        JScrollPane scrollerLeft = new JScrollPane();

        this.clustersTree = new RawClustersTree();
        scrollerLeft.getViewport().add(clustersTree);
        scrollerLeft.setBorder(BorderFactory.createEmptyBorder());
        clustersTree.addTreeSelectionListener(
                new RawClustersTreeSelectionListener(this));

        this.browserView = new WebBrowser();

        JScrollPane scrollerRight = new JScrollPane();
        scrollerRight.setBorder(BorderFactory.createEmptyBorder());
        scrollerRight.getViewport().add(browserView);

        // create 'progress' card.
        JPanel progressPane = new JPanel();
        FormLayout fm = new FormLayout(
                "pref:grow,center:min(pref;200px),pref:grow",
                "40px,default,4px,8px");
        progressPane.setLayout(fm);
        progressInfo = new JLabel("");
        CellConstraints cc = new CellConstraints();
        progressPane.add(progressInfo, cc.xy(2,2));
        JProgressBar pindic = new JProgressBar();
        pindic.setIndeterminate(true);
        progressPane.add(pindic, cc.xy(2,4));

        JSplitPane splitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                scrollerLeft, scrollerRight);
        splitPane.setDividerLocation(300);
        
        this.cards = new JPanel(new CardLayout());
        cards.add(splitPane, MAIN_CARD);
        cards.add(progressPane, PROGRESS_CARD);
        all.add(cards);
        ((CardLayout)cards.getLayout()).show(cards, PROGRESS_CARD);

        this.add(all, BorderLayout.CENTER);        
    }

    /**
     * Shows error information.
     */
    public void showError(final Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        pw.flush();
        final String stackTrace = sw.toString();

        showInfo("<html><body>"
                + "<h1>An exception occurred.</h1>"
                + "<h2>" + e.toString() + "</h2>"
                + "<br><br><pre>"
                + stackTrace
                + "</pre>"
                + "</body></html>");
    }

    /**
     * Updates the HTML view component with the provided HTML.
     * MUST BE INVOKED FROM AN AWT THREAD.
     */
    public void updateDocumentsView(String htmlContent) {
        if (!SwingUtilities.isEventDispatchThread())
            throw new IllegalStateException("Invoke updateDocumentsView() from AWT thread only.");

        this.browserView.setContent(htmlContent);
    }

    /**
     * Leaves only HTML display and sets it to the provided HTML content.
     */
    public void showInfo(final String htmlContent) {
        Runnable task = new Runnable() {
            public void run() {
                updateDocumentsView(htmlContent);
                ((CardLayout)cards.getLayout()).show(cards, MAIN_CARD);
            }
        };
        Browser.runAwtTask(task);
    }

    /**
     * Displays the result of clustering -- clusters and 
     * documents.
     */
    public void showResults(final ClustersConsumerOutputComponent.Result output) {
        Runnable task = new Runnable() {
            public void run() {
                result = output;
                // ok, display the clusters tree first. it is a new clusters
                // model on top of the results
                ResultsTab.this.clustersTree.setModel(new RawClustersTreeModel(output.clusters));
                showAllDocuments();
                ((CardLayout)cards.getLayout()).show(cards, MAIN_CARD);
            }
        };
        Browser.runAwtTask(task);
    }

    /**
     * Displays all documents in the result, regardless of the cluster they are in.
     *
     */
    private void showAllDocuments() {
        if (result == null) return;
        final String html = getHtmlForDocuments(result.documents);

        Runnable task = new Runnable() {
            public void run() {
                ResultsTab.this.clustersTree.clearSelection();
                updateDocumentsView( html );
                ((CardLayout)cards.getLayout()).show(cards, MAIN_CARD);
            }
        };
        Browser.runAwtTask(task);
    }
    
    /**
     * This method converts a set of raw documents to a string.
     */
    public String getHtmlForDocuments(List documents) {
        StringBuffer buffer = new StringBuffer();

        buffer.append("<html><body style=\"font-size: 10pt; font-family: Arial, Helvetica, sans-serif;\">");

        for (Iterator i = documents.iterator(); i.hasNext();) {
            RawDocument doc = (RawDocument) i.next();
            buffer.append("<a href=\"" + doc.getUrl() + "\"><b>");

            if ((doc.getTitle() == null) || (doc.getTitle().length() == 0)) {
                buffer.append(doc.getUrl());
            } else {
                buffer.append(doc.getTitle());
            }

            buffer.append("</b></a>");

            if (doc.getProperty(RawDocument.PROPERTY_LANGUAGE) != null) {
                buffer.append(" [" +
                    doc.getProperty(RawDocument.PROPERTY_LANGUAGE) + "]");
            }

            buffer.append("<br>");

            String r = (String) doc.getProperty(RawDocument.PROPERTY_SNIPPET);

            if (r != null) {
                buffer.append(r);
                buffer.append("<br>");
            }

            buffer.append("<font color=green>");
            buffer.append(doc.getUrl());
            buffer.append("</font><br><br>");
        }

        buffer.append("</ol></body></html>");

        return buffer.toString();
    }

    public void showProgress(final String string) {
        Runnable task = new Runnable() {
            public void run() {
                ((CardLayout)cards.getLayout()).show(cards, PROGRESS_CARD);
                progressInfo.setText(string);
            }
        };
        Browser.runAwtTask(task);
    }

    protected void finalize() throws Throwable {
        super.finalize();
        this.browserView.dispose();
    }    
}
