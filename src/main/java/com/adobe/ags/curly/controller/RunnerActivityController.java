/* 
 * Copyright 2015 Adobe.
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
package com.adobe.ags.curly.controller;

import com.adobe.ags.curly.CurlyApp;
import static com.adobe.ags.curly.Messages.*;
import com.adobe.ags.curly.model.BatchRunnerResult;
import com.adobe.ags.curly.model.RunnerResult;
import com.adobe.ags.curly.model.TaskRunner;
import com.sun.javafx.collections.ObservableListWrapper;
import java.util.Arrays;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.web.WebView;
import javafx.util.StringConverter;

public class RunnerActivityController {
    public static final int REPORT_GENERATION_INTERVAL = 5;
    
    public static enum ReportStyle {
        BRIEF(0), DETAILED(1), FULL(2);
        public int level;
        ReportStyle(int lvl) {
            level = lvl;
        }
    }

    @FXML // fx:id="progressBar"
    private ProgressBar progressBar; // Value injected by FXMLLoader

    @FXML // fx:id="statusLabel"
    private Label statusLabel; // Value injected by FXMLLoader

    @FXML // fx:id="ellapsedLabel"
    private Label ellapsedLabel; // Value injected by FXMLLoader

    @FXML // fx:id="remainingLabel"
    private Label remainingLabel; // Value injected by FXMLLoader

    @FXML // fx:id="reportStyle"
    private ChoiceBox<ReportStyle> reportStyle; // Value injected by FXMLLoader

    @FXML // fx:id="goStopButton"
    private Button goStopButton; // Value injected by FXMLLoader

    @FXML // fx:id="reportWebview"
    private WebView reportWebview; // Value injected by FXMLLoader

    Thread runnerThread = null;
    @FXML
    void goStopClicked(ActionEvent event) {
        if (runnerThread == null) {
            runnerThread = new Thread(currentTask::run);
            runnerThread.start();
            hookupReportGenerator();
            goStopButton.setText(CurlyApp.getMessage(STOP));
        } else {
            CurlyApp.getInstance().runningProperty().set(false);
            goStopButton.disableProperty();
            if (runnerThread != null && runnerThread.isAlive()) {
                runnerThread.interrupt();
                try {
                    runnerThread.join();
                } catch (InterruptedException ex) {
                    Logger.getLogger(RunnerActivityController.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            disableReportGenerator();
        }
    }

    @FXML
    void copyClicked(ActionEvent event) {

    }

    @FXML
    void saveClicked(ActionEvent event) {

    }

    @FXML // This method is called by the FXMLLoader when initialization is complete
    void initialize() {
        assert progressBar != null : "fx:id=\"progressBar\" was not injected: check your FXML file 'RunnerReport.fxml'.";
        assert statusLabel != null : "fx:id=\"statusLabel\" was not injected: check your FXML file 'RunnerReport.fxml'.";
        assert ellapsedLabel != null : "fx:id=\"ellapsedLabel\" was not injected: check your FXML file 'RunnerReport.fxml'.";
        assert remainingLabel != null : "fx:id=\"remainingLabel\" was not injected: check your FXML file 'RunnerReport.fxml'.";
        assert reportStyle != null : "fx:id=\"reportStyle\" was not injected: check your FXML file 'RunnerReport.fxml'.";
        assert goStopButton != null : "fx:id=\"goStopButton\" was not injected: check your FXML file 'RunnerReport.fxml'.";
        assert reportWebview != null : "fx:id=\"reportWebview\" was not injected: check your FXML file 'RunnerReport.fxml'.";

        reportStyle.setItems(new ObservableListWrapper<>(Arrays.asList(ReportStyle.values())));
        reportStyle.setConverter(new StringConverter<RunnerActivityController.ReportStyle>() {
            @Override
            public String toString(ReportStyle style) {
                return CurlyApp.getMessage(style.toString().toLowerCase());
            }

            @Override
            public ReportStyle fromString(String string) {
                return ReportStyle.valueOf(string);
            }
        });
        Platform.runLater(() -> reportStyle.getSelectionModel().selectFirst());
        reportStyle.selectionModelProperty().addListener((prop,oldVal,newVal)->generateReport());
    }

    TaskRunner currentTask;
    RunnerResult results;

    public void attachRunner(TaskRunner runner) {
        currentTask = runner;
        results = runner.getResult();
        if (results instanceof BatchRunnerResult) {
            BatchRunnerResult batchResults = (BatchRunnerResult) results;
            batchResults.timeEllapsedProperty().addListener((observable, oldValue, newValue) -> ellapsedLabel.setText(timeString(newValue.longValue())));
            batchResults.timeRemainingProperty().addListener((observable, oldValue, newValue) -> remainingLabel.setText(timeString(newValue.longValue())));
        } else {
            ellapsedLabel.setText("???");
            remainingLabel.setText("???");
        }
        progressBar.progressProperty().bind(results.percentComplete());
    }

    private String timeString(Long interval) {
        final long hr = TimeUnit.MILLISECONDS.toHours(interval);
        final long min = TimeUnit.MILLISECONDS.toMinutes(interval - TimeUnit.HOURS.toMillis(hr));
        final long sec = TimeUnit.MILLISECONDS.toSeconds(interval - TimeUnit.HOURS.toMillis(hr) - TimeUnit.MINUTES.toMillis(min));
        return String.format("%02d:%02d:%02d", hr, min, sec);
    }
    
    ScheduledExecutorService reportGenerator;
    ScheduledFuture reportTask;
    private void hookupReportGenerator() {
        reportGenerator = new ScheduledThreadPoolExecutor(1);
        reportTask = reportGenerator.scheduleWithFixedDelay(this::generateReport, 1, REPORT_GENERATION_INTERVAL, TimeUnit.SECONDS);
    }
    
    private void disableReportGenerator() {
        reportTask.cancel(false);
        reportGenerator.shutdown();
        reportGenerator = null;
    }    
    
    private void generateReport() {
        if (results != null) {
            reportWebview.getEngine().loadContent(results.toHtml(reportStyle.getSelectionModel().getSelectedItem().level));
        }
    }
}