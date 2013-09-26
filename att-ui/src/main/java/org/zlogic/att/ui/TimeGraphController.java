/*
 * Awesome Time Tracker project.
 * Licensed under Apache 2.0 License: http://www.apache.org/licenses/LICENSE-2.0
 * Author: Dmitry Zolotukhin <zlogic42@outlook.com>
 */
package org.zlogic.att.ui;

import java.net.URL;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import org.zlogic.att.ui.adapters.DataManager;
import org.zlogic.att.ui.adapters.TimeSegmentAdapter;

public class TimeGraphController implements Initializable {

	/**
	 * The logger
	 */
	private final static Logger log = Logger.getLogger(TaskEditorController.class.getName());
	/**
	 * Localization messages
	 */
	private static final ResourceBundle messages = ResourceBundle.getBundle("org/zlogic/att/ui/messages");
	/**
	 * DataManager reference
	 */
	private DataManager dataManager;
	@FXML
	private Pane timeGraphPane;
	private ObservableList<TimeSegmentAdapter> selectedTimeSegments = FXCollections.observableList(new LinkedList<TimeSegmentAdapter>());
	private Point2D dragAnchor;
	
	private enum DragAction {
		
		MOVE, RESIZE
	};
	private DragAction dragAction = DragAction.MOVE;
	private DoubleProperty scale = new SimpleDoubleProperty(0.00005);
	private LongProperty ticksStep = new SimpleLongProperty(0);
	private DoubleProperty layoutPos = new SimpleDoubleProperty(0);
	private int resizeWidth = 10;
	private double minTickSpacing = 100;
	private long timeSegmentGraphicsBinsize = 200000;//TODO: compute this as a product of scale and width, update timeSegmentGraphicsLocations on change
	private Map<TimeSegmentAdapter, TimeSegmentGraphics> timeSegmentGraphics = new HashMap<>();
	private NavigableMap<Long, Set<TimeSegmentGraphics>> timeSegmentGraphicsLocations = new TreeMap<>();
	private Set<TimeSegmentGraphics> visibleTimeSegments = new HashSet<>();
	private BooleanProperty visibleProperty = new SimpleBooleanProperty(false);
	private SimpleDateFormat dateFormat = new SimpleDateFormat(messages.getString("TIMEGRAPH_DATE_TIME_FORMAT"));
	private ListChangeListener<TimeSegmentAdapter> dataManagerListener = new ListChangeListener<TimeSegmentAdapter>() {
		@Override
		public void onChanged(Change<? extends TimeSegmentAdapter> change) {
			if (!visibleProperty.get())
				return;
			while (change.next()) {
				if (change.wasRemoved())
					for (TimeSegmentAdapter timeSegment : change.getRemoved())
						removeTimeSegmentGraphics(timeSegment);
				if (change.wasAdded()) {
					for (TimeSegmentAdapter timeSegment : change.getAddedSubList()) {
						removeTimeSegmentGraphics(timeSegment);
						addTimeSegmentGraphics(timeSegment);
					}
				}
			}
		}
	};
	private ListChangeListener<TimeSegmentAdapter> selectedTimeSegmentsListener = new ListChangeListener<TimeSegmentAdapter>() {
		@Override
		public void onChanged(ListChangeListener.Change<? extends TimeSegmentAdapter> change) {
			if (!visibleProperty.get())
				return;
			while (change.next()) {
				if (change.wasRemoved())
					for (TimeSegmentAdapter timeSegment : change.getRemoved()) {
						TimeSegmentGraphics graphics = timeSegmentGraphics.get(timeSegment);
						if (graphics != null)
							graphics.selectedProperty.set(false);
					}
				if (change.wasAdded())
					for (TimeSegmentAdapter timeSegment : change.getAddedSubList()) {
						TimeSegmentGraphics graphics = timeSegmentGraphics.get(timeSegment);
						if (graphics != null)
							graphics.selectedProperty.set(true);
					}
			}
			
		}
	};
	
	private class TimeSegmentGraphics {
		
		private Rectangle rect, rectLeft, rectRight;
		private Label label;
		private TimeSegmentAdapter timeSegment;
		private Point2D localClick;
		private EventHandler<MouseEvent> mousePressHandler = new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent mouseEvent) {
				localClick = new Point2D(mouseEvent.getX(), mouseEvent.getY());
				dragAction = DragAction.RESIZE;
			}
		};
		private BooleanProperty selectedProperty = new SimpleBooleanProperty(false);
		private boolean initialized = false;
		private BooleanProperty outOfRange = new SimpleBooleanProperty(true);
		private ChangeListener<Date> updateListener = new ChangeListener<Date>() {
			@Override
			public void changed(ObservableValue<? extends Date> observableValue, Date oldValue, Date newValue) {
				if (newValue.equals(oldValue))
					return;
				updateGraphics();
			}
		};
		private ChangeListener<Boolean> outOfRangeListener = new ChangeListener<Boolean>() {
			@Override
			public void changed(ObservableValue<? extends Boolean> observableValue, Boolean oldValue, Boolean newValue) {
				if (newValue)
					dispose();
			}
		};
		private ChangeListener<Number> widthLargerThanZeroListener = new ChangeListener<Number>() {
			@Override
			public void changed(ObservableValue<? extends Number> ov, Number oldValue, Number newValue) {
				if (newValue == oldValue || newValue == null)
					return;
				if ((newValue instanceof Double && (Double) newValue > 0) || newValue.floatValue() > 0) {
					if (!timeGraphPane.getChildren().contains(rectLeft))
						timeGraphPane.getChildren().add(rectLeft);
					if (!timeGraphPane.getChildren().contains(rectRight))
						timeGraphPane.getChildren().add(rectRight);
				} else {
					timeGraphPane.getChildren().removeAll(rectLeft, rectRight);
				}
			}
		};
		private ChangeListener<Boolean> selectedListener = new ChangeListener<Boolean>() {
			@Override
			public void changed(ObservableValue<? extends Boolean> ov, Boolean oldValue, Boolean newValue) {
				if (newValue.equals(oldValue) || rect == null)
					return;
				if (newValue)
					rect.getStyleClass().add("selected-segment"); //NOI18N
				else
					rect.getStyleClass().remove("selected-segment"); //NOI18N
				//Bring selected item to front
				if (newValue)
					toFront();
			}
		};
		private EventHandler<MouseEvent> selectHandler = new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent t) {
				selectedTimeSegments.setAll(timeSegment);
			}
		};
		
		public TimeSegmentGraphics(TimeSegmentAdapter timeSegment) {
			this.timeSegment = timeSegment;
			outOfRange.addListener(outOfRangeListener);
		}
		
		private void setResizeHandleRectProperties(Rectangle rectHandle) {
			rectHandle.setCursor(Cursor.W_RESIZE);
			rectHandle.setWidth(resizeWidth);
			rectHandle.heightProperty().bind(rect.heightProperty());
			rectHandle.layoutYProperty().bind(rect.layoutYProperty());
			rectHandle.getStyleClass().add("timegraph-handle"); //NOI18N
			rectHandle.disableProperty().bind(selectedProperty.not());
		}
		
		private void init() {
			if (initialized)
				return;
			initialized = true;
			
			selectedProperty.set(false);
			selectedProperty.addListener(selectedListener);

			//Init main rectangle
			rect = new Rectangle();
			rect.setHeight(100);
			rect.layoutYProperty().bind(timeGraphPane.heightProperty().subtract(rect.heightProperty()).divide(2));
			rect.getStyleClass().add("timegraph-segment"); //NOI18N
			rect.setCursor(Cursor.DEFAULT);
			timeSegment.startProperty().addListener(updateListener);
			timeSegment.endProperty().addListener(updateListener);

			//Init resize rectangles
			rectLeft = new Rectangle();//TODO: draw vertical label with start/end time
			rectLeft.layoutXProperty().bind(rect.layoutXProperty().subtract(resizeWidth));
			setResizeHandleRectProperties(rectLeft);
			
			rectRight = new Rectangle();
			rectRight.layoutXProperty().bind(rect.layoutXProperty().add(rect.widthProperty()));
			setResizeHandleRectProperties(rectRight);

			//Init text label
			label = new Label();
			label.setAlignment(Pos.CENTER);
			label.layoutXProperty().bind(rect.layoutXProperty());
			label.layoutYProperty().bind(rect.layoutYProperty());
			label.maxHeightProperty().bind(rect.heightProperty());
			label.prefHeightProperty().bind(rect.heightProperty());
			label.maxWidthProperty().bind(rect.widthProperty());
			label.prefWidthProperty().bind(rect.widthProperty());
			label.setLabelFor(rect);
			label.getStyleClass().addAll("timegraph-segment", "text"); //NOI18N
			label.textProperty().bind(timeSegment.fullDescriptionProperty());
			label.visibleProperty().bind(rect.widthProperty().greaterThan(0));

			//Add handlers for resize handles
			rectLeft.setOnMousePressed(mousePressHandler);
			rectRight.setOnMousePressed(mousePressHandler);
			
			rectLeft.setOnMouseDragged(new EventHandler<MouseEvent>() {
				private TimeSegmentGraphics owner;
				
				public EventHandler<MouseEvent> setOwner(TimeSegmentGraphics owner) {
					this.owner = owner;
					return this;
				}
				
				private Date clipDate(Date newStart) {
					Date oldStart = timeSegment.startProperty().get();
					Date clippedStart = newStart;
					for (TimeSegmentGraphics graphics : visibleTimeSegments) {
						if (graphics == owner)
							continue;
						Date end = graphics.timeSegment.endProperty().get();
						if (!end.after(clippedStart))
							continue;
						if ((end.before(oldStart) || end.equals(oldStart)) && (end.after(newStart) || end.equals(newStart)))
							clippedStart = end;
					}
					return clippedStart;
				}
				
				@Override
				public void handle(MouseEvent mouseEvent) {
					if (!timeGraphPane.getChildren().contains(owner.rectLeft))
						return;//Skip drag if handle was hidden
					double clickLocation = localClick != null ? localClick.getX() : 0;
					Date newStart = coordinatesToTime(mouseEvent.getSceneX() - clickLocation);
					if (newStart.after(timeSegment.endProperty().get())) {
						log.finer(messages.getString("START_CANNOT_BE_BEFORE_END_SKIPPING_EDIT"));
					} else if (getIntersectionCount(owner, newStart, timeSegment.endProperty().get()) <= getIntersectionCount(owner, timeSegment.startProperty().get(), timeSegment.endProperty().get())) {
						timeSegment.startProperty().setValue(newStart);
					} else {
						Date clippedStart = clipDate(newStart);
						if (!clippedStart.equals(newStart))
							timeSegment.startProperty().setValue(clippedStart);
					}
					mouseDown(mouseEvent);
				}
			}.setOwner(this));
			rectRight.setOnMouseDragged(new EventHandler<MouseEvent>() {
				private TimeSegmentGraphics owner;
				
				public EventHandler<MouseEvent> setOwner(TimeSegmentGraphics owner) {
					this.owner = owner;
					return this;
				}
				
				private Date clipDate(Date newEnd) {
					Date oldEnd = timeSegment.endProperty().get();
					Date clippedEnd = newEnd;
					for (TimeSegmentGraphics graphics : visibleTimeSegments) {
						if (graphics == owner)
							continue;
						Date start = graphics.timeSegment.startProperty().get();
						if (!start.before(clippedEnd))
							continue;
						if ((start.after(oldEnd) || start.equals(oldEnd)) && (start.before(newEnd) || start.equals(newEnd)))
							clippedEnd = start;
					}
					return clippedEnd;
				}
				
				@Override
				public void handle(MouseEvent mouseEvent) {
					if (!timeGraphPane.getChildren().contains(owner.rectRight))
						return;//Skip drag if handle was hidden
					double clickLocation = localClick != null ? (resizeWidth - localClick.getX()) : 0;
					Date newEnd = coordinatesToTime(mouseEvent.getSceneX() + clickLocation);
					if (newEnd.before(timeSegment.startProperty().get())) {
						log.finer(messages.getString("START_CANNOT_BE_BEFORE_END_SKIPPING_EDIT"));
					} else if (getIntersectionCount(owner, timeSegment.startProperty().get(), newEnd) <= getIntersectionCount(owner, timeSegment.startProperty().get(), timeSegment.endProperty().get())) {
						timeSegment.endProperty().setValue(newEnd);
					} else {
						Date clippedEnd = clipDate(newEnd);
						if (!clippedEnd.equals(newEnd))
							timeSegment.endProperty().setValue(clippedEnd);
					}
					mouseDown(mouseEvent);
				}
			}.setOwner(this));

			//Update rectangle width
			rect.widthProperty().addListener(widthLargerThanZeroListener);
			updateGraphics();

			//Add handler for main rectangle
			rect.setOnMouseClicked(selectHandler);
			label.setOnMouseClicked(selectHandler);

			//Add everything to the graph
			timeGraphPane.getChildren().addAll(rect);
			timeGraphPane.getChildren().addAll(label);
			BooleanBinding outOfRangeExpression = rectLeft.layoutXProperty().greaterThan(timeGraphPane.layoutXProperty().add(timeGraphPane.widthProperty()))
					.or(rectRight.layoutXProperty().add(rectRight.widthProperty()).lessThan(timeGraphPane.layoutXProperty()));
			outOfRange.bind(outOfRangeExpression);
			outOfRangeListener.changed(outOfRangeExpression, true, outOfRangeExpression.get());

			//Add to visible graphics list
			visibleTimeSegments.add(this);

			//Set selected style
			if (selectedTimeSegments != null)
				selectedProperty.set(selectedTimeSegments.contains(timeSegment));
		}
		
		private void toFront() {
			if (!initialized)
				return;
			rect.toFront();
			rectLeft.toFront();
			rectRight.toFront();
			label.toFront();
		}
		
		private void updateGraphics() {
			if (!initialized)
				return;
			long start = timeSegment.startProperty().get().getTime();
			long end = timeSegment.endProperty().get().getTime();
			long duration = end - start;
			rect.layoutXProperty().bind(timeToCoordinatesProperty(timeSegment.startProperty().get()).add(resizeWidth));
			rect.widthProperty().bind(scale.multiply(duration).subtract(resizeWidth * 2));
			updateTimeSegmentGraphics(this);
			updateTimeSegmentGraphics();
		}
		
		public void dispose() {
			if (initialized) {
				//TODO: do not dispose if object is being dragged
				timeSegment.startProperty().removeListener(updateListener);
				timeSegment.endProperty().removeListener(updateListener);
				selectedProperty.removeListener(selectedListener);
				timeGraphPane.getChildren().removeAll(label, rectLeft, rectRight, rect);
				rect = null;
				rectLeft = null;
				rectRight = null;
				rect = null;
				initialized = false;
				outOfRange.unbind();
				outOfRange.set(true);
				visibleTimeSegments.remove(this);
			}
		}
	}
	
	private class Tick {
		
		private Line line;
		private Label label;
		
		public Tick(Line line, Label label) {
			this.line = line;
			this.label = label;
			timeGraphPane.getChildren().addAll(line, label);
			label.toBack();
			line.toBack();
		}
		
		public void dispose() {
			timeGraphPane.getChildren().removeAll(line, label);
		}
	}
	private NavigableMap<Long, Tick> ticks = new TreeMap<>();

	/**
	 * Initializes the controller
	 *
	 * @param url initialization URL
	 * @param resourceBundle supplied resources
	 */
	@Override
	public void initialize(URL url, ResourceBundle resourceBundle) {
		timeGraphPane.setCursor(Cursor.MOVE);
		selectedTimeSegments.addListener(selectedTimeSegmentsListener);
		
		visibleProperty.addListener(new ChangeListener<Boolean>() {
			@Override
			public void changed(ObservableValue<? extends Boolean> observableValue, Boolean oldValue, Boolean newValue) {
				if (newValue == oldValue)
					return;
				if (newValue.equals(Boolean.TRUE)) {
					updateTimescale();
					dataManager.getTimeSegments().addListener(dataManagerListener);
				} else {
					dataManager.getTimeSegments().removeListener(dataManagerListener);
					clearTimeScale();
				}
			}
		});
		
		updateTicksStep();
		
		scale.addListener(new ChangeListener<Number>() {
			@Override
			public void changed(ObservableValue<? extends Number> observableValue, Number oldValue, Number newValue) {
				if (!newValue.equals(oldValue))
					return;
				updateTicksStep();
			}
		});
		
		timeGraphPane.widthProperty().addListener(new ChangeListener<Number>() {
			@Override
			public void changed(ObservableValue<? extends Number> observableValue, Number oldValue, Number newValue) {
				if (!oldValue.equals(newValue)) {
					updateTicks();
					updateTimeSegmentGraphics();
				}
			}
		});
	}

	/**
	 * Sets the DataManager reference
	 *
	 * @param dataManager the DataManager reference
	 */
	public void setDataManager(DataManager dataManager) {
		this.dataManager = dataManager;
	}

	/**
	 * The visibility property (if anything is going to be rendered)
	 *
	 * @return the visibility property
	 */
	public BooleanProperty visibleProperty() {
		return visibleProperty;
	}
	
	public void setSelectedTimeSegments(List<TimeSegmentAdapter> selectedTimeSegments) {
		this.selectedTimeSegments.setAll(selectedTimeSegments);
	}

	/**
	 * Performs a full refresh of the time scale
	 */
	private void updateTimescale() {
		clearTimeScale();
		if (dragAnchor == null) {
			//Graph was not moved - so we can jump to the latest time
			Date latestDate = null;
			for (TimeSegmentAdapter timeSegment : dataManager.getTimeSegments())
				if (latestDate == null || timeSegment.endProperty().get().after(latestDate))
					latestDate = timeSegment.endProperty().get();
			if (latestDate != null)
				layoutPos.bind(timeGraphPane.widthProperty()/*.negate().*/.subtract(scale.multiply(latestDate.getTime())));
		}
		
		for (TimeSegmentAdapter timeSegment : dataManager.getTimeSegments()) {
			addTimeSegmentGraphics(timeSegment);
		}
		updateTicks();
		
		updateTimeSegmentGraphics();//TODO: also call this when tasks are added or changed; a started offscreen task can slowly crawl into the visible space!
	}

	/**
	 * Deletes all items from the time scale
	 */
	private void clearTimeScale() {
		synchronized (timeSegmentGraphicsLocations) {
			timeSegmentGraphicsLocations.clear();
			for (TimeSegmentGraphics graphics : timeSegmentGraphics.values())
				graphics.dispose();
			timeSegmentGraphics.clear();
		}
	}
	
	private void removeTimeSegmentGraphics(TimeSegmentAdapter timeSegment) {
		TimeSegmentGraphics graphics = timeSegmentGraphics.remove(timeSegment);
		if (graphics != null) {
			boolean found = false;
			synchronized (timeSegmentGraphicsLocations) {
				for (Map.Entry<Long, Set<TimeSegmentGraphics>> entry : timeSegmentGraphicsLocations.entrySet())
					found |= entry.getValue().remove(graphics);
			}
			graphics.dispose();
		}
	}
	
	private void addTimeSegmentGraphics(TimeSegmentAdapter timeSegment) {
		long startBin = timeSegmentGraphicsBinsize * (timeSegment.startProperty().get().getTime() / timeSegmentGraphicsBinsize);
		long endBin = timeSegmentGraphicsBinsize * (timeSegment.endProperty().get().getTime() / timeSegmentGraphicsBinsize + 1);
		TimeSegmentGraphics graphics = new TimeSegmentGraphics(timeSegment);
		timeSegmentGraphics.put(timeSegment, graphics);
		synchronized (timeSegmentGraphicsLocations) {
			for (long bin = startBin; bin <= endBin; bin += timeSegmentGraphicsBinsize) {
				if (!timeSegmentGraphicsLocations.containsKey(bin))
					timeSegmentGraphicsLocations.put(bin, new HashSet<TimeSegmentGraphics>());
				timeSegmentGraphicsLocations.get(bin).add(graphics);
			}
		}
	}
	
	private void updateTimeSegmentGraphics(TimeSegmentGraphics graphics) {
		long startBin = timeSegmentGraphicsBinsize * (graphics.timeSegment.startProperty().get().getTime() / timeSegmentGraphicsBinsize);
		long endBin = timeSegmentGraphicsBinsize * (graphics.timeSegment.endProperty().get().getTime() / timeSegmentGraphicsBinsize + 1);
		synchronized (timeSegmentGraphicsLocations) {
			//Remove from non-matching bins
			for (Map.Entry<Long, Set<TimeSegmentGraphics>> entry : timeSegmentGraphicsLocations.entrySet())
				if (entry.getKey() < startBin || entry.getKey() > endBin)
					entry.getValue().remove(graphics);
			//Add to any bins we aren't already in
			for (long bin = startBin; bin <= endBin; bin += timeSegmentGraphicsBinsize) {
				if (!timeSegmentGraphicsLocations.containsKey(bin))
					timeSegmentGraphicsLocations.put(bin, new HashSet<TimeSegmentGraphics>());
				timeSegmentGraphicsLocations.get(bin).add(graphics);
			}
		}
	}
	
	private void updateTicksStep() {
		long step = 1000;//1 second is the minimum //TODO: move this into constants
		long stepMultipliers[] = new long[]{1, 2, 5};
		int stepMultiplier = 0;
		while ((step * stepMultipliers[stepMultiplier] * scale.get()) < minTickSpacing && step < Long.MAX_VALUE && step > 0) {
			stepMultiplier++;
			if (stepMultiplier >= (stepMultipliers.length - 1)) {
				stepMultiplier = 0;
				step *= 10;
			}
		}
		if (step < Long.MAX_VALUE && step > 0) {
			ticksStep.set(step * stepMultipliers[stepMultiplier]);
		} else {
			ticksStep.set(0);
			log.severe(MessageFormat.format(messages.getString("STEP_IS_OUT_OF_RANGE"), new Object[]{step}));
		}
	}
	
	private void updateTimeSegmentGraphics() {
		long startTime = coordinatesToTime(timeGraphPane.getLayoutX()).getTime();
		long endTime = coordinatesToTime(timeGraphPane.getLayoutX() + timeGraphPane.getWidth()).getTime();
		//Initialize new objects
		for (NavigableMap.Entry<Long, Set<TimeSegmentGraphics>> entry : timeSegmentGraphicsLocations.subMap(startTime, true, endTime, true).entrySet())
			for (TimeSegmentGraphics graphics : entry.getValue())
				if (!graphics.initialized)
					graphics.init();
	}
	
	private void updateTicks() {
		if (ticksStep.get() <= 0)
			return;
		long startTick = ticksStep.get() * (coordinatesToTime(timeGraphPane.getLayoutX()).getTime() / ticksStep.get());
		long endTick = ticksStep.get() * (coordinatesToTime(timeGraphPane.getLayoutX() + timeGraphPane.getWidth()).getTime() / ticksStep.get() + 1);
		Map<Long, Tick> newTicks = new HashMap<>();
		//Generate new ticks
		for (long currentTick = startTick; currentTick <= endTick; currentTick += ticksStep.get()) {
			if (ticks.containsKey(currentTick)) {
				newTicks.put(currentTick, ticks.get(currentTick));
				ticks.remove(currentTick);
				continue;
			}
			Line line = new Line();
			line.endYProperty().bind(timeGraphPane.heightProperty());
			line.layoutXProperty().bind(timeToCoordinatesProperty(new Date(currentTick)));
			line.getStyleClass().add("timegraph-tick"); //NOI18N

			Label label = new Label(dateFormat.format(new Date(currentTick)));
			label.setLabelFor(line);
			label.layoutXProperty().bind(line.layoutXProperty().subtract(label.widthProperty().divide(2)));
			label.getStyleClass().add("timegraph-tick"); //NOI18N

			line.layoutYProperty().bind(label.layoutYProperty().add(label.heightProperty()));
			
			newTicks.put(currentTick, new Tick(line, label));
		}

		//Dispose unused ticks
		for (Tick oldTick : ticks.values())
			oldTick.dispose();
		ticks.clear();
		ticks.putAll(newTicks);
	}
	
	private int getIntersectionCount(TimeSegmentGraphics segmentGraphics, Date segmentStartTime, Date segmentEndTime) {
		//Check the current intersections count
		int currentIntersectionsCount = 0;
		for (TimeSegmentGraphics graphics : visibleTimeSegments)
			if (graphics != segmentGraphics) {
				Date start = graphics.timeSegment.startProperty().get();
				Date end = graphics.timeSegment.endProperty().get();
				if (segmentStartTime.before(start) && segmentEndTime.after(start))
					currentIntersectionsCount++;
				else if (segmentStartTime.before(end) && segmentEndTime.after(end))
					currentIntersectionsCount++;
			}
		return currentIntersectionsCount;
	}
	
	private DoubleBinding timeToCoordinatesProperty(Date time) {
		return timeGraphPane.layoutXProperty().add(layoutPos).add(scale.multiply(time.getTime()));
	}
	
	private double timeToCoordinates(Date time) {
		return timeGraphPane.layoutXProperty().get() + layoutPos.get() + scale.get() * (time.getTime());
	}
	
	private Date coordinatesToTime(Double coordinates) {
		return new Date((long) ((coordinates - layoutPos.get()) / scale.get()));
	}
	
	@FXML
	private void mouseDown(MouseEvent event) {
		dragAnchor = new Point2D(event.getSceneX(), event.getSceneY());
		event.consume();
	}
	
	@FXML
	private void mouseUp(MouseEvent event) {
		dragAction = DragAction.MOVE;
	}
	
	@FXML
	private void mouseDragged(MouseEvent event) {
		if (dragAction == DragAction.MOVE) {
			double deltaX = event.getX() - dragAnchor.getX();
			if (layoutPos.isBound())
				layoutPos.unbind();
			layoutPos.set(layoutPos.get() + deltaX);
			mouseDown(event);
			updateTicks();
			updateTimeSegmentGraphics();
		}
	}
}
