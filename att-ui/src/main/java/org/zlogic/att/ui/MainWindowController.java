/*
 * Awesome Time Tracker project.
 * License TBD.
 * Author: Dmitry Zolotukhin <zlogic@gmail.com>
 */
package org.zlogic.att.ui;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Comparator;
import java.util.Date;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ListChangeListener.Change;
import javafx.concurrent.Task;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.converter.DateTimeStringConverter;
import javafx.util.converter.DefaultStringConverter;
import org.zlogic.att.data.converters.Exporter;
import org.zlogic.att.data.converters.GrindstoneImporter;
import org.zlogic.att.data.converters.Importer;
import org.zlogic.att.data.converters.XmlExporter;
import org.zlogic.att.data.converters.XmlImporter;
import org.zlogic.att.ui.adapters.CustomFieldAdapter;
import org.zlogic.att.ui.adapters.CustomFieldValueAdapter;
import org.zlogic.att.ui.adapters.TaskAdapter;
import org.zlogic.att.ui.adapters.TaskManager;
import org.zlogic.att.ui.adapters.TimeSegmentAdapter;

/**
 * Controller for the main window
 *
 * @author Dmitry Zolotukhin <zlogic@gmail.com>
 */
public class MainWindowController implements Initializable {

	/**
	 * The logger
	 */
	private final static Logger log = Logger.getLogger(MainWindowController.class.getName());
	/**
	 * Localization messages
	 */
	private static final ResourceBundle messages = ResourceBundle.getBundle("org/zlogic/att/ui/messages");
	/**
	 * TaskManager reference
	 */
	private TaskManager taskManager;
	/**
	 * Last opened directory
	 */
	private ObjectProperty<File> lastDirectory = new SimpleObjectProperty<>();
	/**
	 * Easy access to preference storage
	 */
	protected java.util.prefs.Preferences preferenceStorage = java.util.prefs.Preferences.userNodeForPackage(Launcher.class);
	/**
	 * The background task thread (used for synchronization & clean termination)
	 */
	protected Thread backgroundThread;
	/**
	 * The background task
	 */
	protected Task<Void> backgroundTask;
	/**
	 * Task to be performed before shutdown/exit
	 */
	private Runnable shutdownProcedure;
	/**
	 * Custom field editor stage
	 */
	private Stage customFieldEditorStage;
	/**
	 * Custom field editor controller
	 */
	private CustomFieldEditorController customFieldEditorController;
	/**
	 * Report stage
	 */
	private Stage reportStage;
	/**
	 * Report controller
	 */
	private ReportController reportController;
	/**
	 * Filters stage
	 */
	private Stage filterEditorStage;
	/**
	 * Report controller
	 */
	private FilterEditorController filterEditorController;
	/**
	 * Root pane
	 */
	@FXML
	private VBox rootPane;
	/**
	 * Task editor controller
	 */
	@FXML
	private TaskEditorController taskEditorController;
	/**
	 * Tasks list table
	 */
	@FXML
	private TableView<TaskAdapter> taskList;
	/**
	 * Task name column
	 */
	@FXML
	private TableColumn<TaskAdapter, String> columnTaskName;
	/**
	 * Task total time column
	 */
	@FXML
	private TableColumn<TaskAdapter, String> columnTotalTime;
	/**
	 * Task first time column
	 */
	@FXML
	private TableColumn<TaskAdapter, Date> columnFirstTime;
	/**
	 * Task last time column
	 */
	@FXML
	private TableColumn<TaskAdapter, Date> columnLastTime;
	/**
	 * Task completed column
	 */
	@FXML
	private TableColumn<TaskAdapter, Boolean> columnTaskCompleted;
	/**
	 * Label to indicate the active task
	 */
	@FXML
	private Label activeTaskLabel;
	/**
	 * Duplicate task button
	 */
	@FXML
	private Button duplicateTaskButton;
	/**
	 * Delete task button
	 */
	@FXML
	private Button deleteTaskButton;
	/**
	 * Active task pane (can be hidden)
	 */
	@FXML
	private HBox activeTaskPane;
	/**
	 * Status pane
	 */
	@FXML
	private HBox statusPane;
	/**
	 * Progress indicator
	 */
	@FXML
	private ProgressIndicator progressIndicator;
	/**
	 * Progress indicator label
	 */
	@FXML
	private Label progressLabel;
	/**
	 * Cleanup DB menu item
	 */
	@FXML
	private MenuItem menuItemCleanupDB;
	/**
	 * Import Awesome Time Tracker XML data menu item
	 */
	@FXML
	private MenuItem menuItemImportAwesomeTimeTrackerXml;
	/**
	 * Export Awesome Time Tracker XML data menu item
	 */
	@FXML
	private MenuItem menuItemExportAwesomeTimeTrackerXml;
	/**
	 * Property to store the number of selected tasks
	 */
	@FXML
	private IntegerProperty taskSelectionSize = new SimpleIntegerProperty(0);

	/**
	 * Initializes the controller
	 *
	 * @param url initialization URL
	 * @param resourceBundle supplied resources
	 */
	@Override
	public void initialize(URL url, ResourceBundle resourceBundle) {
		//Default sort order
		taskList.getSortOrder().add(columnLastTime);
		columnLastTime.setSortType(TableColumn.SortType.DESCENDING);
		//Task comparator
		Comparator<Date> TaskComparator = new Comparator<Date>() {
			@Override
			public int compare(Date o1, Date o2) {
				if (o1 == null && o2 != null)
					return 1;
				if (o1 != null && o2 == null)
					return -1;
				if (o1 == null && o2 == null)
					return 0;
				return o1.compareTo(o2);
			}
		};
		columnLastTime.setComparator(TaskComparator);
		//Create the task manager
		taskManager = new TaskManager();
		taskList.setItems(taskManager.getTasks());
		reloadTasks();

		//Auto update sort order
		taskManager.taskUpdatedProperty().addListener(new ChangeListener<Date>() {
			@Override
			public void changed(ObservableValue<? extends Date> ov, Date oldValue, Date newValue) {
				updateSortOrder();
			}
		});

		//Update the selection size property
		taskList.getSelectionModel().getSelectedItems().addListener(new ListChangeListener<TaskAdapter>() {
			@Override
			public void onChanged(Change<? extends TaskAdapter> change) {
				taskSelectionSize.set(change.getList().size());
			}
		});

		taskList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		//Bind the current task panel
		activeTaskPane.managedProperty().bind(activeTaskPane.visibleProperty());
		activeTaskPane.visibleProperty().bind(taskManager.timingSegmentProperty().isNotNull());
		taskManager.timingSegmentProperty().addListener(new ChangeListener<TimeSegmentAdapter>() {
			private ChangeListener<TaskAdapter> taskChangedListener = new ChangeListener<TaskAdapter>() {
				@Override
				public void changed(ObservableValue<? extends TaskAdapter> ov, TaskAdapter oldValue, TaskAdapter newValue) {
					activeTaskLabel.textProperty().unbind();
					activeTaskLabel.textProperty().bind(newValue.nameProperty());
				}
			};

			@Override
			public void changed(ObservableValue<? extends TimeSegmentAdapter> ov, TimeSegmentAdapter oldValue, TimeSegmentAdapter newValue) {
				if (newValue != null && newValue.equals(oldValue))
					return;
				if (oldValue != null)
					oldValue.ownerTaskProperty().removeListener(taskChangedListener);
				if (newValue != null) {
					activeTaskLabel.textProperty().unbind();
					activeTaskLabel.textProperty().bind(newValue.ownerTaskProperty().get().nameProperty());
					newValue.ownerTaskProperty().addListener(taskChangedListener);
				}
			}
		});

		deleteTaskButton.disableProperty().bind(taskSelectionSize.lessThanOrEqualTo(0));
		duplicateTaskButton.disableProperty().bind(taskSelectionSize.lessThanOrEqualTo(0));
		//Restore settings
		lastDirectory.addListener(new ChangeListener<File>() {
			@Override
			public void changed(ObservableValue<? extends File> ov, File oldFile, File newFile) {
				preferenceStorage.put("lastDirectory", newFile.toString()); //NOI18N
			}
		});
		lastDirectory.set(preferenceStorage.get("lastDirectory", null) == null ? null : new File(preferenceStorage.get("lastDirectory", null))); //NOI18N
		//Row properties
		taskList.setRowFactory(new Callback<TableView<TaskAdapter>, TableRow<TaskAdapter>>() {
			@Override
			public TableRow<TaskAdapter> call(TableView<TaskAdapter> p) {
				TableRow<TaskAdapter> row = new TableRow<>();
				row.itemProperty().addListener(new ChangeListener<TaskAdapter>() {
					private TableRow<TaskAdapter> row;
					private ChangeListener timingChangeListener = new ChangeListener<Boolean>() {
						@Override
						public void changed(ObservableValue<? extends Boolean> ov, Boolean oldValue, Boolean newValue) {
							if (newValue != null) {
								if (newValue)
									row.getStyleClass().add("timing-segment"); //NOI18N
								else
									row.getStyleClass().remove("timing-segment"); //NOI18N
							}
						}
					};

					public ChangeListener<TaskAdapter> setRow(TableRow<TaskAdapter> row) {
						this.row = row;
						return this;
					}

					@Override
					public void changed(ObservableValue<? extends TaskAdapter> ov, TaskAdapter oldValue, TaskAdapter newValue) {
						if (oldValue != null)
							oldValue.isTimingProperty().removeListener(timingChangeListener);
						if (newValue != null) {
							newValue.isTimingProperty().addListener(timingChangeListener);
							timingChangeListener.changed(newValue.isTimingProperty(), oldValue != null ? oldValue.isTimingProperty().get() : false, newValue.isTimingProperty().get());
						}
					}
				}.setRow(row));
				//Drag'n'drop support
				//TODO: create a separate class
				row.setOnDragEntered(new EventHandler<DragEvent>() {
					private TableRow<TaskAdapter> row;

					public EventHandler<DragEvent> setRow(TableRow<TaskAdapter> row) {
						this.row = row;
						return this;
					}

					@Override
					public void handle(DragEvent event) {
						if (event.getGestureSource() instanceof TableView && ((TableView) event.getGestureSource()).getSelectionModel().getSelectedItem() instanceof TimeSegmentAdapter)
							row.getStyleClass().add("drag-accept-task"); //NOI18N
						event.consume();
					}
				}.setRow(row));
				row.setOnDragExited(new EventHandler<DragEvent>() {
					private TableRow<TaskAdapter> row;

					public EventHandler<DragEvent> setRow(TableRow<TaskAdapter> row) {
						this.row = row;
						return this;
					}

					@Override
					public void handle(DragEvent event) {
						row.getStyleClass().remove("drag-accept-task"); //NOI18N
						event.consume();
					}
				}.setRow(row));
				row.setOnDragOver(new EventHandler<DragEvent>() {
					@Override
					public void handle(DragEvent event) {
						if (event.getGestureSource() instanceof TableView && ((TableView) event.getGestureSource()).getSelectionModel().getSelectedItem() instanceof TimeSegmentAdapter)
							event.acceptTransferModes(TransferMode.MOVE);
						event.consume();
					}
				});
				row.setOnDragDropped(new EventHandler<DragEvent>() {
					private TableRow<TaskAdapter> row;

					public EventHandler<DragEvent> setRow(TableRow<TaskAdapter> row) {
						this.row = row;
						return this;
					}

					@Override
					public void handle(DragEvent event) {
						boolean success = false;
						if (event.getGestureSource() instanceof TableView) {
							Object selectedItem = ((TableView) event.getGestureSource()).getSelectionModel().getSelectedItem();
							if (selectedItem instanceof TimeSegmentAdapter) {
								event.setDropCompleted(success);
								((TimeSegmentAdapter) selectedItem).ownerTaskProperty().set(row.getItem());
							}
						}
						event.setDropCompleted(success);
						event.consume();
					}
				}.setRow(row));
				return row;
			}
		;
		});
		//Cell editors
		columnTaskName.setCellFactory(new Callback<TableColumn<TaskAdapter, String>, TableCell<TaskAdapter, String>>() {
			@Override
			public TableCell<TaskAdapter, String> call(TableColumn<TaskAdapter, String> p) {
				TextFieldTableCell<TaskAdapter, String> cell = new TextFieldTableCell<>();
				cell.setConverter(new DefaultStringConverter());
				return cell;
			}
		});
		columnTaskCompleted.setCellFactory(new Callback<TableColumn<TaskAdapter, Boolean>, TableCell<TaskAdapter, Boolean>>() {
			@Override
			public TableCell<TaskAdapter, Boolean> call(TableColumn<TaskAdapter, Boolean> taskAdapterBooleanTableColumn) {
				CheckBoxTableCell<TaskAdapter, Boolean> cell = new CheckBoxTableCell<>();
				cell.setAlignment(Pos.CENTER);
				return cell;
			}
		});
		columnTotalTime.setCellFactory(new Callback<TableColumn<TaskAdapter, String>, TableCell<TaskAdapter, String>>() {
			@Override
			public TableCell<TaskAdapter, String> call(TableColumn<TaskAdapter, String> p) {
				TextFieldTableCell<TaskAdapter, String> cell = new TextFieldTableCell<>();
				cell.setConverter(new DefaultStringConverter());
				cell.setAlignment(Pos.CENTER_RIGHT);
				return cell;
			}
		});
		columnFirstTime.setCellFactory(new Callback<TableColumn<TaskAdapter, Date>, TableCell<TaskAdapter, Date>>() {
			@Override
			public TableCell<TaskAdapter, Date> call(TableColumn<TaskAdapter, Date> p) {
				TextFieldTableCell<TaskAdapter, Date> cell = new TextFieldTableCell<>();
				cell.setConverter(new DateTimeStringConverter());
				cell.setAlignment(Pos.CENTER_RIGHT);
				return cell;
			}
		});
		columnLastTime.setCellFactory(new Callback<TableColumn<TaskAdapter, Date>, TableCell<TaskAdapter, Date>>() {
			@Override
			public TableCell<TaskAdapter, Date> call(TableColumn<TaskAdapter, Date> p) {
				TextFieldTableCell<TaskAdapter, Date> cell = new TextFieldTableCell<>();
				cell.setConverter(new DateTimeStringConverter());
				cell.setAlignment(Pos.CENTER_RIGHT);
				return cell;
			}
		});

		//Set column sizes
		//TODO: make sure this keeps working correctly
		//taskList.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
		columnTaskName.prefWidthProperty().bind(taskList.widthProperty().multiply(10).divide(20));
		columnTotalTime.prefWidthProperty().bind(taskList.widthProperty().multiply(2).divide(20));
		columnFirstTime.prefWidthProperty().bind(taskList.widthProperty().multiply(3).divide(20));
		columnLastTime.prefWidthProperty().bind(taskList.widthProperty().multiply(3).divide(20));
		columnTaskCompleted.prefWidthProperty().bind(taskList.widthProperty().multiply(2).divide(20).subtract(15));

		//Menu items and status pane
		menuItemCleanupDB.disableProperty().bind(statusPane.visibleProperty());
		menuItemImportAwesomeTimeTrackerXml.disableProperty().bind(statusPane.visibleProperty());
		menuItemExportAwesomeTimeTrackerXml.disableProperty().bind(statusPane.visibleProperty());
		statusPane.managedProperty().bind(statusPane.visibleProperty());
		statusPane.setVisible(false);

		//Load other windows
		loadWindowCustomFieldEditor();
		loadWindowReport();
		loadWindowFilterEditor();
		taskEditorController.setTaskManager(taskManager);
	}

	/**
	 * Assigns the shutdown procedure to be performed before quitting Java FX
	 * application
	 *
	 * @param shutdownProcedure the shutdown procedure
	 */
	public void setShutdownProcedure(Runnable shutdownProcedure) {
		this.shutdownProcedure = shutdownProcedure;
	}

	/**
	 * Loads the custom field editor FXML
	 */
	private void loadWindowCustomFieldEditor() {
		//Load FXML
		customFieldEditorStage = new Stage();
		customFieldEditorStage.initModality(Modality.NONE);
		Parent root = null;
		FXMLLoader loader = new FXMLLoader(getClass().getResource("CustomFieldEditor.fxml"), messages); //NOI18N
		loader.setLocation(getClass().getResource("CustomFieldEditor.fxml")); //NOI18N
		try {
			root = (Parent) loader.load();
		} catch (IOException ex) {
			Logger.getLogger(getClass().getName()).log(Level.SEVERE, messages.getString("ERROR_LOADING_FXML"), ex);
		}
		//Initialize the scene properties
		if (root != null) {
			Scene scene = new Scene(root);
			customFieldEditorStage.setTitle(messages.getString("CUSTOM_FIELD_EDITOR"));
			customFieldEditorStage.setScene(scene);
		}
		//Set the task manager
		customFieldEditorController = loader.getController();
		customFieldEditorController.setTaskManager(taskManager);
	}

	/**
	 * Loads the report FXML
	 */
	private void loadWindowReport() {
		//Load FXML
		reportStage = new Stage();
		reportStage.initModality(Modality.NONE);
		Parent root = null;
		FXMLLoader loader = new FXMLLoader(getClass().getResource("Report.fxml"), messages); //NOI18N
		loader.setLocation(getClass().getResource("Report.fxml")); //NOI18N
		try {
			root = (Parent) loader.load();
		} catch (IOException ex) {
			log.log(Level.SEVERE, messages.getString("ERROR_LOADING_FXML"), ex);
		}
		//Initialize the scene properties
		if (root != null) {
			Scene scene = new Scene(root);
			reportStage.setTitle(messages.getString("REPORT"));
			reportStage.setScene(scene);
		}
		//Set the task manager
		reportController = loader.getController();
		reportController.setTaskManager(taskManager);
		reportController.setLastDirectory(lastDirectory);
	}

	/**
	 * Loads the report FXML
	 */
	private void loadWindowFilterEditor() {
		//Load FXML
		filterEditorStage = new Stage();
		filterEditorStage.initModality(Modality.NONE);
		Parent root = null;
		FXMLLoader loader = new FXMLLoader(getClass().getResource("FilterEditor.fxml"), messages); //NOI18N
		loader.setLocation(getClass().getResource("FilterEditor.fxml")); //NOI18N
		try {
			root = (Parent) loader.load();
		} catch (IOException ex) {
			log.log(Level.SEVERE, messages.getString("ERROR_LOADING_FXML"), ex);
		}
		//Initialize the scene properties
		if (root != null) {
			Scene scene = new Scene(root);
			filterEditorStage.setTitle(messages.getString("FILTERS"));
			filterEditorStage.setScene(scene);
		}
		//Set the task manager
		filterEditorController = loader.getController();
		filterEditorController.setTaskManager(taskManager);
	}

	/**
	 * Reloads tasks
	 */
	protected void reloadTasks() {
		taskManager.reloadTasks();
		taskEditorController.setEditedTaskList(taskList.getSelectionModel().getSelectedItems());
		updateSortOrder();
	}

	/**
	 * Updates the tasks sort order
	 */
	private void updateSortOrder() {
		//FIXME: Remove this after it's fixed in Java FX
		//TODO: call this on task updates?
		if (taskList.getEditingCell() != null && taskList.getEditingCell().getRow() >= 0)
			return;
		TableColumn<TaskAdapter, ?>[] sortOrder = taskList.getSortOrder().toArray(new TableColumn[0]);
		taskEditorController.setIgnoreEditedTaskUpdates(true);
		taskList.getSortOrder().clear();
		taskList.getSortOrder().addAll(sortOrder);
		taskEditorController.setIgnoreEditedTaskUpdates(false);
	}

	/*
	 * Background task processing
	 */
	/**
	 * Starts the background task (disables task-related components, show the
	 * progress pane)
	 */
	private void beginBackgroundTask() {
		statusPane.setVisible(true);
	}

	/**
	 * Ends the background task (enables task-related components, hides the
	 * progress pane)
	 */
	private void endBackgroundTask() {
		statusPane.setVisible(false);
	}

	/**
	 * Starts a task in a background thread
	 *
	 * @param task the task to be started
	 */
	protected void startTaskThread(Task<Void> task) {
		synchronized (this) {
			//Wait for previous task to compete
			completeTaskThread();
			backgroundTask = task;

			progressIndicator.progressProperty().bind(task.progressProperty());
			progressLabel.textProperty().bind(task.messageProperty());

			//Automatically run beginTask/endTask before the actual task is processed
			backgroundThread = new Thread(
					new Runnable() {
						protected Task<Void> task;

						public Runnable setTask(Task<Void> task) {
							this.task = task;
							return this;
						}

						@Override
						public void run() {
							beginBackgroundTask();
							task.run();
							endBackgroundTask();
						}
					}.setTask(task));
			backgroundThread.setDaemon(true);
			backgroundThread.start();
		}
	}

	/**
	 * Waits for the current task to complete
	 */
	protected void completeTaskThread() {
		synchronized (this) {
			if (backgroundThread != null) {
				try {
					backgroundThread.join();
					backgroundThread = null;
				} catch (InterruptedException ex) {
					Logger.getLogger(MainWindowController.class.getName()).log(Level.SEVERE, null, ex);
				}
			}
			if (backgroundTask != null) {
				progressIndicator.progressProperty().unbind();
				progressLabel.textProperty().unbind();
				backgroundTask = null;
			}
		}
	}

	/*
	 * Callbacks
	 */
	/**
	 * Create a new task
	 */
	@FXML
	private void createNewTask() {
		TaskAdapter newTask = taskManager.createTask();
		taskList.getSelectionModel().clearSelection();
		updateSortOrder();
		taskList.getSelectionModel().select(newTask);
	}

	/**
	 * Delete all selected tasks
	 */
	@FXML
	private void deleteSelectedTasks() {
		for (TaskAdapter selectedTask : taskList.getSelectionModel().getSelectedItems())
			taskManager.deleteTask(selectedTask);
		updateSortOrder();
	}

	/**
	 * Duplicate all selected tasks
	 */
	@FXML
	private void duplicateSelectedTasks() {
		for (TaskAdapter selectedTask : taskList.getSelectionModel().getSelectedItems()) {
			TaskAdapter newTask = taskManager.createTask();
			newTask.nameProperty().set(selectedTask.nameProperty().get());
			newTask.descriptionProperty().set(selectedTask.descriptionProperty().get());
			for (CustomFieldAdapter customField : taskManager.getCustomFields()) {
				CustomFieldValueAdapter customFieldValue = new CustomFieldValueAdapter(customField, taskManager);
				customFieldValue.setTask(newTask);
				customFieldValue.valueProperty().set(selectedTask.getTask().getCustomField(customField.getCustomField()));
			}
		}
		updateSortOrder();
	}

	/**
	 * Show the custom field editor window
	 */
	@FXML
	private void showCustomFieldEditor() {
		customFieldEditorStage.getIcons().setAll(((Stage) rootPane.getScene().getWindow()).getIcons());
		customFieldEditorStage.show();
	}

	/**
	 * Show the custom report window
	 */
	@FXML
	private void showReportWindow() {
		reportStage.getIcons().setAll(((Stage) rootPane.getScene().getWindow()).getIcons());
		reportStage.show();
	}

	@FXML
	private void showFiltersEditor() {
		filterEditorStage.getIcons().setAll(((Stage) rootPane.getScene().getWindow()).getIcons());
		filterEditorStage.show();
	}

	/**
	 * Exit the application
	 */
	@FXML
	private void exit() {
		if (shutdownProcedure != null)
			shutdownProcedure.run();
	}

	/**
	 * Import Grindstone data
	 */
	@FXML
	private void importGrindstoneData() {
		//Prepare file chooser dialog
		FileChooser fileChooser = new FileChooser();
		if (lastDirectory.get() != null && lastDirectory.get().exists())
			fileChooser.setInitialDirectory(lastDirectory.get());
		fileChooser.setTitle(messages.getString("CHOOSE_FILE_TO_IMPORT"));
		//Prepare file chooser filter
		fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(messages.getString("EXPORTED_GRINDSTONE_XML_FILES"), "*.xml")); //NOI18N

		//Show the dialog
		File selectedFile;
		if ((selectedFile = fileChooser.showOpenDialog(rootPane.getScene().getWindow())) != null) {
			lastDirectory.set(selectedFile.isDirectory() ? selectedFile : selectedFile.getParentFile());

			//Choose the importer based on the file extension
			Importer importer = null;
			String extension = selectedFile.isFile() ? selectedFile.getName().substring(selectedFile.getName().lastIndexOf(".")) : null; //NOI18N
			if (extension.equals(".xml")) { //NOI18N
				log.fine(messages.getString("EXTENSION_MATCHED"));
				importer = new GrindstoneImporter(selectedFile);
			}
			//Import data
			if (importer != null)
				taskManager.getPersistenceHelper().importData(importer);
			else
				log.fine(messages.getString("EXTENSION_NOT_RECOGNIZED"));
			reloadTasks();
		}
	}

	/**
	 * Import XML data
	 */
	@FXML
	private void importXmlData() {
		//Prepare file chooser dialog
		FileChooser fileChooser = new FileChooser();
		if (lastDirectory.get() != null && lastDirectory.get().exists())
			fileChooser.setInitialDirectory(lastDirectory.get());
		fileChooser.setTitle(messages.getString("CHOOSE_FILE_TO_IMPORT"));
		//Prepare file chooser filter
		fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(messages.getString("XML_FILES"), "*.xml")); //NOI18N

		//Show the dialog
		File selectedFile;
		if ((selectedFile = fileChooser.showOpenDialog(rootPane.getScene().getWindow())) != null) {
			lastDirectory.set(selectedFile.isDirectory() ? selectedFile : selectedFile.getParentFile());

			//Choose the importer based on the file extension
			Importer importer = null;
			String extension = selectedFile.isFile() ? selectedFile.getName().substring(selectedFile.getName().lastIndexOf(".")) : null; //NOI18N
			if (extension.equals(".xml")) { //NOI18N
				log.fine(messages.getString("EXTENSION_MATCHED"));
				importer = new XmlImporter(selectedFile);
			}
			//Import data


			//Prepare the task
			Task<Void> task = new Task<Void>() {
				private Importer importer;

				public Task<Void> setImporter(Importer importer) {
					this.importer = importer;
					return this;
				}

				@Override
				protected Void call() throws Exception {
					updateMessage(messages.getString("IMPORTING_DATA"));
					updateProgress(-1, 1);

					//Import data
					if (importer != null)
						taskManager.getPersistenceHelper().importData(importer);
					else
						log.fine(messages.getString("EXTENSION_NOT_RECOGNIZED"));

					updateProgress(1, 1);
					updateMessage(""); //NOI18N

					taskManager.reloadCustomFields();
					reloadTasks();
					return null;
				}
			}.setImporter(importer);
			//Run the task
			startTaskThread(task);

		}
	}

	/**
	 * Export XML data
	 */
	@FXML
	private void exportXmlData() {
		// Prepare file chooser dialog
		FileChooser fileChooser = new FileChooser();
		if (lastDirectory.get() != null && lastDirectory.get().exists())
			fileChooser.setInitialDirectory(lastDirectory.get());
		fileChooser.setTitle(messages.getString("CHOOSE_WHERE_TO_EXPORT"));
		//Prepare file chooser filter
		fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(messages.getString("XML_FILES"), "*.xml")); //NOI18N

		//Show the dialog
		File selectedFile;
		if ((selectedFile = fileChooser.showSaveDialog(rootPane.getScene().getWindow())) != null) {
			lastDirectory.set(selectedFile.isDirectory() ? selectedFile : selectedFile.getParentFile());

			//Append extension if needed
			String extension = selectedFile.isFile() ? selectedFile.getName().substring(selectedFile.getName().lastIndexOf(".")) : null; //NOI18N
			if (extension == null || extension.isEmpty())
				selectedFile = new File(selectedFile.getParentFile(), selectedFile.getName() + ".xml"); //NOI18N

			Exporter exporter = new XmlExporter(selectedFile);
			//Prepare the task
			Task<Void> task = new Task<Void>() {
				private Exporter exporter;

				public Task<Void> setExporter(Exporter exporter) {
					this.exporter = exporter;
					return this;
				}

				@Override
				protected Void call() throws Exception {
					updateMessage(messages.getString("EXPORTING_DATA"));
					updateProgress(-1, 1);

					//Export data
					if (exporter != null)
						exporter.exportData(taskManager.getPersistenceHelper());
					else
						log.fine(messages.getString("EXTENSION_NOT_RECOGNIZED"));

					updateProgress(1, 1);
					updateMessage(""); //NOI18N
					return null;
				}
			}.setExporter(exporter);
			//Run the task
			startTaskThread(task);
		}
	}

	/**
	 * Stop timing the currently timing task
	 */
	@FXML
	private void stopTimingTask() {
		TimeSegmentAdapter segment = taskManager.timingSegmentProperty().get();
		if (segment != null)
			segment.stopTiming();
	}

	/**
	 * Perform DB cleanup functions
	 */
	@FXML
	private void cleanupDB() {
		//Prepare the task
		Task<Void> task = new Task<Void>() {
			@Override
			protected Void call() throws Exception {
				updateMessage(messages.getString("CLEANING_UP_DB"));
				updateProgress(-1, 1);

				taskManager.getPersistenceHelper().cleanupDB();

				updateProgress(1, 1);
				updateMessage(""); //NOI18N
				return null;
			}
		};
		//Run the task
		startTaskThread(task);
	}
}