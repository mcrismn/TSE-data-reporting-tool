package tse_summarized_information;

import java.io.IOException;
import java.util.Collection;

import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;

import app_config.DebugConfig;
import dataset.DatasetStatus;
import global_utils.Warnings;
import report.ReportException;
import table_database.TableDao;
import table_dialog.DialogBuilder;
import table_dialog.RowValidatorLabelProvider;
import table_relations.Relation;
import table_skeleton.TableColumnValue;
import table_skeleton.TableRow;
import table_skeleton.TableVersion;
import tse_case_report.CaseReportDialog;
import tse_components.TableDialogWithMenu;
import tse_config.CatalogLists;
import tse_config.CustomStrings;
import tse_main.UIActions;
import tse_report.TseReport;
import tse_validator.SummarizedInfoValidator;
import webservice.MySOAPException;
import xlsx_reader.TableSchema;
import xlsx_reader.TableSchemaList;
import xml_catalog_reader.Selection;

/**
 * Class which allows adding and editing a summarized information report.
 * @author avonva
 *
 */
public class SummarizedInfoDialog extends TableDialogWithMenu {

	private TseReport report;
	
	public SummarizedInfoDialog(Shell parent) {
		
		super(parent, "", false, false);
		
		// create the parent structure
		super.create();
		
		// default disabled
		setRowCreationEnabled(false);
		
		// add 300 px in height
		addHeight(300);
		
		// add the parents of preferences and settings
		try {
			addParentTable(Relation.getGlobalParent(CustomStrings.PREFERENCES_SHEET));
			addParentTable(Relation.getGlobalParent(CustomStrings.SETTINGS_SHEET));
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		// if double clicked an element of the table
		// open the cases
		addTableDoubleClickListener(new IDoubleClickListener() {
			
			@Override
			public void doubleClick(DoubleClickEvent event) {

				final IStructuredSelection selection = (IStructuredSelection)event.getSelection();
				if (selection == null || selection.isEmpty())
					return;

				TableRow summInfo = (TableRow) selection.getFirstElement();

				// first validate the content of the row
				if (!validate(summInfo))
					return;
				
				// create default cases if no cases
				// and cases were set in the aggregated data
				if (!hasCases(summInfo) && getNumberOfExpectedCases(summInfo) > 0) {
					try {
						createDefaultCases(summInfo);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}

				// open cases dialog
				openCases(summInfo);
			}
		});
	}
	
	/**
	 * Validate the row
	 * @param summInfo
	 * @return
	 */
	private boolean validate(TableRow summInfo) {
		
		if (!summInfo.areMandatoryFilled()) {
			warnUser("Error", "Cannot add cases. Mandatory data are missing!");
			return false;
		}

		boolean hasCases = hasCases(summInfo);
		int expected = getNumberOfExpectedCases(summInfo);

		if (expected == 0 && !hasCases) {
			warnUser("Error", 
					"No positive or inconclusive cases can be detailed, please check fields Positive and Inconclusive in this table.");
			return false;
		}

		return true;
	}
	
	/**
	 * get the declared number of cases in the current row
	 * @param summInfo
	 * @return
	 */
	private int getNumberOfExpectedCases(TableRow summInfo) {
		
		int positive = summInfo.getNumLabel(CustomStrings.SUMMARIZED_INFO_POS_SAMPLES);
		int inconclusive = summInfo.getNumLabel(CustomStrings.SUMMARIZED_INFO_INC_SAMPLES);
		int total = positive + inconclusive;
		
		return total;
	}
	
	/**
	 * Check if the summ info has cases or not
	 * @param summInfo
	 * @return
	 */
	private boolean hasCases(TableRow summInfo) {

		TableSchema schema = TableSchemaList.getByName(CustomStrings.CASE_INFO_SHEET);
		
		if (schema == null)
			return false;
		
		TableDao dao = new TableDao(schema);

		boolean hasCases = !dao.getByParentId(summInfo.getSchema().getSheetName(), summInfo.getId()).isEmpty();

		return hasCases;
	}

	/**
	 * Open the cases dialog of the summarized information
	 */
	private void openCases(TableRow summInfo) {
		
		// create a case passing also the report information
		CaseReportDialog dialog = new CaseReportDialog(getDialog(), getParentFilter(), summInfo);
		
		// filter the records by the clicked summarized information
		dialog.setParentFilter(summInfo);
		
		// add as parent also the report of the summarized information
		// which is the parent filter since we have chosen a summarized
		// information from a single report (the summ info were filtered
		// by the report)
		dialog.addParentTable(getParentFilter());
		
		dialog.open();
		
		// refresh the table when cases are changed
		refresh(summInfo);
	}
	
	/**
	 * Once a summ info is clicked, create the default cases according to 
	 * number of positive/inconclusive cases
	 * @param summInfo
	 * @param positive
	 * @param inconclusive
	 * @throws IOException
	 */
	private void createDefaultCases(TableRow summInfo) throws IOException {
		
		// check cases number
		int positive = summInfo.getNumLabel(CustomStrings.SUMMARIZED_INFO_POS_SAMPLES);
		int inconclusive = summInfo.getNumLabel(CustomStrings.SUMMARIZED_INFO_INC_SAMPLES);
		
		TableSchema resultSchema = TableSchemaList.getByName(CustomStrings.CASE_INFO_SHEET);
		
		TableRow resultRow = new TableRow(resultSchema);
		
		// inject the case parent to the result
		Relation.injectParent(report, resultRow);
		Relation.injectParent(summInfo, resultRow);

		// add two default rows
		TableDao dao = new TableDao(resultSchema);
		
		resultRow.initialize();
		
		// for each inconclusive
		for (int i = 0; i < inconclusive; ++i) {
			
			// add get the id and update the fields
			int id = dao.add(resultRow);
			resultRow.setId(id);
			
			resultRow.initialize();
			
			// set assessment as inconclusive
			TableColumnValue value = new TableColumnValue();
			value.setCode(CustomStrings.DEFAULT_ASSESS_INC_CASE_CODE);
			value.setLabel(CustomStrings.DEFAULT_ASSESS_INC_CASE_LABEL);
			resultRow.put(CustomStrings.CASE_INFO_ASSESS, value);
			
			dao.update(resultRow);
		}
		
		// for each positive
		for (int i = 0; i < positive; ++i) {
			
			// add get the id and update the fields
			int id = dao.add(resultRow);
			resultRow.setId(id);
			resultRow.initialize();
			dao.update(resultRow);
		}
	}
	
	@Override
	public void setParentFilter(TableRow parentFilter) {
		
		this.report = new TseReport(parentFilter);
		
		// update ui with report data
		updateUI();
		
		super.setParentFilter(parentFilter);
	}
	
	/**
	 * Get the report that contains the summarized information
	 * @return
	 */
	public TseReport getReport() {
		return report;
	}
	
	@Override
	public void clear() {
		super.clear();
		this.report = null;
		initUI(); // report was closed => update ui
	}

	/**
	 * Create a new row with default values
	 * @param element
	 * @return
	 * @throws IOException 
	 */
	@Override
	public TableRow createNewRow(TableSchema schema, Selection element) {

		TableColumnValue value = new TableColumnValue(element);
		
		// create a new summarized information
		return new SummarizedInfo(CustomStrings.SUMMARIZED_INFO_TYPE, value);
	}

	@Override
	public String getSchemaSheetName() {
		return CustomStrings.SUMMARIZED_INFO_SHEET;
	}

	@Override
	public boolean apply(TableSchema schema, Collection<TableRow> rows, TableRow selectedRow) {
		return true;
	}

	@Override
	public Collection<TableRow> loadInitialRows(TableSchema schema, TableRow parentFilter) {
		return null;
	}

	@Override
	public void processNewRow(TableRow row) {}
	
	@Override
	public RowValidatorLabelProvider getValidator() {
		return new SummarizedInfoValidator();
	}

	@Override
	public void addWidgets(DialogBuilder viewer) {
		
		SelectionListener refreshStateListener = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent arg0) {

				// if no report opened, stop
				if (report == null)
					return;

				UIActions.refreshStatus(getDialog(), report, new Listener() {

					@Override
					public void handleEvent(Event arg0) {
						updateUI();
					}
				});
			}
		};
		
		SelectionListener editListener = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent arg0) {
				
				// if no report opened, stop
				if (report == null)
					return;
				
				int val = warnUser("Warning", 
						"The report editing will be enabled, but be aware this will overwrite the current data.", 
						SWT.ICON_WARNING | SWT.YES | SWT.NO);
				
				if (val == SWT.NO)
					return;
				
				// yes, overwrite
				report.makeEditable();
				report.update();
				
				// update the ui accordingly
				updateUI();
			}
		};
		
		SelectionListener sendListener = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent arg0) {
				
				if (report == null)
					return;
				
				if (report.isEmpty()) {
					Warnings.warnUser(getDialog(), "Error", "Cannot send an empty report!");
					return;
				}
				
				try {
					UIActions.send(getDialog(), report);
				} catch (MySOAPException e) {
					e.printStackTrace();
					UIActions.showSOAPWarning(getDialog(), e.getError());
				} catch (ReportException e) {
					e.printStackTrace();
					Warnings.warnUser(getDialog(), "Error", "Something went wrong, "
							+ "please check if the report senderDatasetId is set");
				}
				
				getDialog().setCursor(getDialog().getDisplay().getSystemCursor(SWT.CURSOR_ARROW));
			}
		};
		
		SelectionListener rejectListener = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent arg0) {
				
				if (report == null)
					return;
				
				// reject the report and update the ui
				UIActions.reject(getDialog(), report, new Listener() {
					
					@Override
					public void handleEvent(Event arg0) {
						updateUI();
					}
				});
			}
		};
		
		SelectionListener submitListener = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent arg0) {
				
				if (report == null)
					return;
				
				// reject the report and update the ui
				UIActions.submit(getDialog(), report, new Listener() {
					
					@Override
					public void handleEvent(Event arg0) {
						updateUI();
					}
				});
			}
		};
		
		SelectionListener displayAckListener = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent arg0) {
				
				if (report == null)
					return;
				
				UIActions.displayAck(getDialog(), report);
			}
		};
		
		SelectionListener amendListener = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent arg0) {
				
				if (report == null)
					return;
				
				UIActions.amend(getDialog(), report, new Listener() {
					
					@Override
					public void handleEvent(Event arg0) {
						TseReport newReportVersion = (TseReport) arg0.data;
						setParentFilter(newReportVersion);
					}
				});
			}
		};
		
		viewer.addHelp("TSEs monitoring data (aggregated level)")
		
			.addComposite("labelsComp", new GridLayout(1, false), null)
			.addLabelToComposite("reportLabel", "labelsComp")
			.addLabelToComposite("statusLabel", "labelsComp")
			.addLabelToComposite("messageIdLabel", "labelsComp")
			.addLabelToComposite("datasetIdLabel", "labelsComp")
			
			.addComposite("panel", new GridLayout(2, false), null)
			
			.addGroupToComposite("rowCreatorComp", "panel", "Add record", new GridLayout(1, false), null)
			.addRowCreatorToComposite("rowCreatorComp", "Add data related to monitoring of:", CatalogLists.TSE_LIST)
			
			.addGroupToComposite("buttonsComp", "panel", "Toolbar", new GridLayout(7, false), null)
			
			.addButtonToComposite("editBtn", "buttonsComp", "Edit", editListener)
			.addButtonToComposite("sendBtn", "buttonsComp", "Send", sendListener)
			.addButtonToComposite("submitBtn", "buttonsComp", "Submit", submitListener)
			.addButtonToComposite("amendBtn", "buttonsComp", "Amend", amendListener)
			.addButtonToComposite("rejectBtn", "buttonsComp", "Reject", rejectListener)
			.addButtonToComposite("refreshBtn", "buttonsComp", "Refresh status", refreshStateListener)
			.addButtonToComposite("displayAckBtn", "buttonsComp", "Display ack", displayAckListener)
			
			.addTable(CustomStrings.SUMMARIZED_INFO_SHEET, true);

		initUI();
	}
	
	/**
	 * Initialize the labels to their initial state
	 */
	private void initUI() {
		
		DialogBuilder panel = getPanelBuilder();
		
		// disable refresh until a report is opened
		panel.setEnabled("refreshBtn", false);
		panel.setEnabled("editBtn", false);
		panel.setEnabled("sendBtn", false);
		panel.setEnabled("rejectBtn", false);
		panel.setEnabled("submitBtn", false);
		panel.setEnabled("amendBtn", false);
		panel.setEnabled("displayAckBtn", false);
		
		// add image to edit button
		Image editImage = new Image(Display.getCurrent(), this.getClass()
				.getClassLoader().getResourceAsStream("edit-icon.png"));
		panel.addButtonImage("editBtn", editImage);
		
		// add image to send button
		Image sendImage = new Image(Display.getCurrent(), this.getClass()
				.getClassLoader().getResourceAsStream("send-icon.png"));
		panel.addButtonImage("sendBtn", sendImage);
		
		// add image to refresh button
		Image submitImage = new Image(Display.getCurrent(), this.getClass()
				.getClassLoader().getResourceAsStream("submit-icon.png"));
		panel.addButtonImage("submitBtn", submitImage);
		
		// add image to send button
		Image amendImage = new Image(Display.getCurrent(), this.getClass()
				.getClassLoader().getResourceAsStream("amend-icon.png"));
		panel.addButtonImage("amendBtn", amendImage);
		
		// add image to send button
		Image rejectImage = new Image(Display.getCurrent(), this.getClass()
				.getClassLoader().getResourceAsStream("reject-icon.png"));
		panel.addButtonImage("rejectBtn", rejectImage);
		
		// add image to send button
		Image displayAckImage = new Image(Display.getCurrent(), this.getClass()
				.getClassLoader().getResourceAsStream("displayAck-icon.png"));
		panel.addButtonImage("displayAckBtn", displayAckImage);
		
		// add image to refresh button
		Image refreshImage = new Image(Display.getCurrent(), this.getClass()
				.getClassLoader().getResourceAsStream("refresh-icon.png"));
		panel.addButtonImage("refreshBtn", refreshImage);
		
		panel.setLabelText("reportLabel", "Monthly report: no report is currently opened!");
		panel.setLabelText("statusLabel", "Status: -");
		panel.setLabelText("messageIdLabel", "DCF Message ID: -");
		panel.setLabelText("datasetIdLabel", "DCF Dataset ID: -");
	}
	
	/**
	 * Update the ui using the report information
	 * @param report
	 */
	public void updateUI() {
		
		String reportMonth = report.getLabel(CustomStrings.REPORT_MONTH);
		String reportYear = report.getYear();
		String status = report.getStatus().getStatus();
		String messageId = report.getMessageId();
		String datasetId = report.getDatasetId();
		
		StringBuilder reportRow = new StringBuilder();
		reportRow.append("Monthly report: ")
			.append(reportMonth)
			.append(" ")
			.append(reportYear);
		
		// add version if possible
		if (!TableVersion.isFirstVersion(report.getVersion())) {
			reportRow.append(" revision ")
				.append(Integer.valueOf(report.getVersion())); // remove 0 from 01..
		}
		
		StringBuilder statusRow = new StringBuilder("Status: ");
		statusRow.append(checkField(status, DatasetStatus.DRAFT.getStatus()));
		
		StringBuilder messageRow = new StringBuilder("DCF Message ID: ");
		messageRow.append(checkField(messageId, "not assigned yet"));

		StringBuilder datasetRow = new StringBuilder("DCF Dataset ID: ");
		datasetRow.append(checkField(datasetId, "not assigned yet"));
		
		DialogBuilder panel = getPanelBuilder();
		panel.setLabelText("reportLabel", reportRow.toString());
		panel.setLabelText("statusLabel", statusRow.toString());
		panel.setLabelText("messageIdLabel", messageRow.toString());
		panel.setLabelText("datasetIdLabel", datasetRow.toString());
		
		// enable the table only if report status if correct
		DatasetStatus datasetStatus = DatasetStatus.fromString(status);
		boolean editableReport = datasetStatus.isEditable();
		panel.setTableEditable(editableReport);
		panel.setRowCreatorEnabled(editableReport);
		
		panel.setEnabled("editBtn", datasetStatus.canBeMadeEditable());
		panel.setEnabled("sendBtn", datasetStatus.canBeSent());
		panel.setEnabled("amendBtn", DebugConfig.debug || datasetStatus.canBeAmended());
		panel.setEnabled("submitBtn", datasetStatus.canBeSubmitted());
		panel.setEnabled("rejectBtn", datasetStatus.canBeRejected());
		panel.setEnabled("displayAckBtn", datasetStatus.canDisplayAck());
		panel.setEnabled("refreshBtn", datasetStatus.canBeRefreshed());
	}
	
	/**
	 * Check if a field is null or empty. If so return the default value,
	 * otherwise return the field itself.
	 * @param field
	 * @param defaultValue
	 * @return
	 */
	private String checkField(String field, String defaultValue) {
		
		String out = null;
		
		if (field != null && !field.isEmpty())
			out = field;
		else
			out = defaultValue;
		
		return out;
	}
}