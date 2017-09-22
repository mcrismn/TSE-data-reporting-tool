package tse_components;

import java.io.IOException;
import java.util.Collection;

import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Shell;

import table_dialog.TableViewWithHelp.RowCreationMode;
import table_skeleton.TableRow;
import tse_config.CustomPaths;
import xlsx_reader.TableSchema;
import xml_catalog_reader.Selection;

/**
 * Class which allows adding and editing a summarized information report.
 * @author avonva
 *
 */
public class CaseReportDialog extends TableDialogWithMenu {
	
	public CaseReportDialog(Shell parent, TableRow report) {
		
		super(parent, "Case report", "TSEs monitoring data (case level)", 
				true, RowCreationMode.STANDARD, true, false);
		
		// add 300 px in height
		addDialogHeight(300);
		
		// specify title and list of the selector
		setRowCreatorLabel("Add data:");
		
		// set the report also as parent of the case
		addParentTable(report);
		
		addTableDoubleClickListener(new IDoubleClickListener() {
			
			@Override
			public void doubleClick(DoubleClickEvent event) {
				
				final IStructuredSelection selection = (IStructuredSelection)event.getSelection();
				if (selection == null || selection.isEmpty())
					return;

				final TableRow caseReport = (TableRow) selection.getFirstElement();
				
				// initialize result passing also the 
				// report data and the summarized information data
				ResultDialog dialog = new ResultDialog(parent, report, getParentFilter());
				dialog.setParentFilter(caseReport); // set the case as filter (and parent)
				dialog.open();
			}
		});
	}
	
	@Override
	public void setParentFilter(TableRow parentFilter) {
		setRowCreationEnabled(parentFilter != null);
		super.setParentFilter(parentFilter);
	}

	/**
	 * Create a new row with default values
	 * @param element
	 * @return
	 * @throws IOException 
	 */
	@Override
	public TableRow createNewRow(TableSchema schema, Selection element) {
		
		TableRow row = new TableRow(schema);
		return row;
	}

	@Override
	public String getSchemaSheetName() {
		return CustomPaths.CASE_INFO_SHEET;
	}

	@Override
	public boolean apply(TableSchema schema, Collection<TableRow> rows, TableRow selectedRow) {
		return true;
	}

	@Override
	public Collection<TableRow> loadInitialRows(TableSchema schema, TableRow parentFilter) {
		return null;
	}
}
