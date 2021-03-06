// Copyright (C) 2014 Guibing Guo
//
// This file is part of LibRec.
//
// LibRec is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// LibRec is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with LibRec. If not, see <http://www.gnu.org/licenses/>.
//

package librec.data;

import happy.coding.math.Stats;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;

/**
 * Data Structure: Sparse Matrix whose implementation is modified from M4J
 * library
 * 
 * <ul>
 * <li><a href="http://netlib.org/linalg/html_templates/node91.html">Compressed
 * Row Storage (CRS)</a></li>
 * <li><a href="http://netlib.org/linalg/html_templates/node92.html">Compressed
 * Col Storage (CCS)</a></li>
 * </ul>
 * 
 * @author guoguibing
 * 
 */
public class SparseMatrix implements Iterable<MatrixEntry> {

	// matrix dimension
	protected int numRows, numCols;

	// Compressed Row Storage (CRS)
	protected double[] rowData;
	protected int[] rowPtr, colInd;

	// Compressed Col Storage (CCS)
	protected double[] colData;
	protected int[] colPtr, rowInd;

	// is CCS enabled
	protected boolean isCCSUsed = false;

	/**
	 * Construct a sparse matrix with both CRS and CCS structures
	 */
	public SparseMatrix(int rows, int cols, Table<Integer, Integer, Double> dataTable, Multimap<Integer, Integer> colMap) {
		numRows = rows;
		numCols = cols;

		construct(dataTable, colMap);
	}

	/**
	 * Construct a sparse matrix with only CRS structures
	 */
	public SparseMatrix(int rows, int cols, Table<Integer, Integer, Double> dataTable) {
		this(rows, cols, dataTable, null);
	}

	/**
	 * Define a sparse matrix without data, only use for {@code transpose}
	 * method
	 * 
	 */
	private SparseMatrix(int rows, int cols) {
		numRows = rows;
		numCols = cols;
	}

	public SparseMatrix(SparseMatrix mat) {
		this(mat, true);
	}

	/**
	 * Construct a sparse matrix from another sparse matrix
	 * 
	 * @param mat
	 *            the original sparse matrix
	 * @param deap
	 *            whether to copy the CCS structures
	 */
	public SparseMatrix(SparseMatrix mat, boolean deap) {
		numRows = mat.numRows;
		numCols = mat.numCols;

		copyCRS(mat.rowData, mat.rowPtr, mat.colInd);

		if (deap && mat.isCCSUsed)
			copyCCS(mat.colData, mat.colPtr, mat.rowInd);
	}

	private void copyCRS(double[] data, int[] ptr, int[] idx) {
		rowData = new double[data.length];
		for (int i = 0; i < rowData.length; i++)
			rowData[i] = data[i];

		rowPtr = new int[ptr.length];
		for (int i = 0; i < rowPtr.length; i++)
			rowPtr[i] = ptr[i];

		colInd = new int[idx.length];
		for (int i = 0; i < colInd.length; i++)
			colInd[i] = idx[i];
	}

	private void copyCCS(double[] data, int[] ptr, int[] idx) {
		colData = new double[data.length];
		for (int i = 0; i < colData.length; i++)
			colData[i] = data[i];

		colPtr = new int[ptr.length];
		for (int i = 0; i < colPtr.length; i++)
			colPtr[i] = ptr[i];

		rowInd = new int[idx.length];
		for (int i = 0; i < rowInd.length; i++)
			rowInd[i] = idx[i];
	}

	/**
	 * Make a deep clone of current matrix
	 */
	public SparseMatrix clone() {
		return new SparseMatrix(this);
	}

	/**
	 * @return the transpose of current matrix
	 */
	public SparseMatrix transpose() {
		if (isCCSUsed) {
			SparseMatrix tr = new SparseMatrix(numCols, numRows);

			tr.copyCRS(this.rowData, this.rowPtr, this.colInd);
			tr.copyCCS(this.colData, this.colPtr, this.rowInd);

			return tr;
		} else {
			Table<Integer, Integer, Double> dataTable = HashBasedTable.create();
			for (MatrixEntry me : this)
				dataTable.put(me.column(), me.row(), me.get());
			return new SparseMatrix(numCols, numRows, dataTable);
		}
	}

	/**
	 * @return the row pointers of CRS structure
	 */
	public int[] getRowPointers() {
		return rowPtr;
	}

	/**
	 * @return the column indices of CCS structure
	 */
	public int[] getColumnIndices() {
		return colInd;
	}

	/**
	 * @return the cardinary of current matrix
	 */
	public int size() {
		int size = 0;

		for (MatrixEntry me : this)
			if (me.get() != 0)
				size++;

		return size;
	}

	/**
	 * Construct a sparse matrix
	 * 
	 * @param dataTable
	 *            data table
	 * @param columnStructure
	 *            column structure
	 */
	private void construct(Table<Integer, Integer, Double> dataTable, Multimap<Integer, Integer> columnStructure) {
		int nnz = dataTable.size();

		// CRS
		rowPtr = new int[numRows + 1];
		colInd = new int[nnz];
		rowData = new double[nnz];

		int j = 0;
		for (int i = 1; i <= numRows; ++i) {
			Set<Integer> cols = dataTable.row(i - 1).keySet();
			rowPtr[i] = rowPtr[i - 1] + cols.size();

			for (int col : cols) {
				colInd[j++] = col;
				if (col < 0 || col >= numCols)
					throw new IllegalArgumentException("colInd[" + j + "]=" + col
							+ ", which is not a valid column index");
			}

			Arrays.sort(colInd, rowPtr[i - 1], rowPtr[i]);
		}

		// CCS
		if (columnStructure != null) {
			colPtr = new int[numCols + 1];
			rowInd = new int[nnz];
			colData = new double[nnz];
			isCCSUsed = true;

			j = 0;
			for (int i = 1; i <= numCols; ++i) {
				// dataTable.col(i-1) is very time-consuming
				Collection<Integer> rows = columnStructure.get(i - 1);
				colPtr[i] = colPtr[i - 1] + rows.size();

				for (int row : rows) {
					rowInd[j++] = row;
					if (row < 0 || row >= numRows)
						throw new IllegalArgumentException("rowInd[" + j + "]=" + row
								+ ", which is not a valid row index");
				}

				Arrays.sort(rowInd, colPtr[i - 1], colPtr[i]);
			}
		}

		// set data
		for (Cell<Integer, Integer, Double> en : dataTable.cellSet()) {
			int row = en.getRowKey();
			int col = en.getColumnKey();
			double val = en.getValue();

			set(row, col, val);
		}
	}

	/**
	 * @return number of rows
	 */
	public int numRows() {
		return numRows;
	}

	/**
	 * @return number of columns
	 */
	public int numColumns() {
		return numCols;
	}

	/**
	 * @return referce to the data of current matrix
	 */
	public double[] getData() {
		return rowData;
	}

	/**
	 * Set a value to entry [row, column]
	 * 
	 * @param row
	 *            row id
	 * @param column
	 *            column id
	 * @param val
	 *            value to set
	 */
	public void set(int row, int column, double val) {
		int index = getCRSIndex(row, column);
		rowData[index] = val;

		if (isCCSUsed) {
			index = getCCSIndex(row, column);
			colData[index] = val;
		}
	}

	/**
	 * Add a value to entry [row, column]
	 * 
	 * @param row
	 *            row id
	 * @param column
	 *            column id
	 * @param val
	 *            value to add
	 */
	public void add(int row, int column, double val) {
		int index = getCRSIndex(row, column);
		rowData[index] += val;

		if (isCCSUsed) {
			index = getCCSIndex(row, column);
			colData[index] += val;
		}
	}

	/**
	 * Retrieve value at entry [row, column]
	 * 
	 * @param row
	 *            row id
	 * @param column
	 *            column id
	 * @return value at entry [row, column]
	 */
	public double get(int row, int column) {

		int index = Arrays.binarySearch(colInd, rowPtr[row], rowPtr[row + 1], column);

		if (index >= 0)
			return rowData[index];
		else
			return 0;
	}

	/**
	 * get a row sparse vector of a matrix
	 * 
	 * @param row
	 *            row id
	 * @return a sparse vector of {index, value}
	 * 
	 */
	public SparseVector row(int row) {

		SparseVector sv = new SparseVector(numCols);

		for (int j = rowPtr[row]; j < rowPtr[row + 1]; j++) {
			int col = colInd[j];
			double val = get(row, col);
			if (val != 0.0)
				sv.set(col, val);
		}

		return sv;
	}

	/**
	 * get a row sparse vector of a matrix
	 * 
	 * @param row
	 *            row id
	 * @param except
	 *            row id to be excluded
	 * @return a sparse vector of {index, value}
	 * 
	 */
	public SparseVector row(int row, int except) {

		SparseVector sv = new SparseVector(numCols);

		for (int j = rowPtr[row]; j < rowPtr[row + 1]; j++) {
			int col = colInd[j];
			if (col != except) {
				double val = get(row, col);
				if (val != 0.0)
					sv.set(col, val);
			}
		}
		return sv;
	}

	/**
	 * query the size of a specific row
	 * 
	 * @param row
	 *            row id
	 * @return the size of non-zero elements of a row
	 */
	public int rowSize(int row) {

		int size = 0;
		for (int j = rowPtr[row]; j < rowPtr[row + 1]; j++) {
			int col = colInd[j];
			if (get(row, col) != 0.0)
				size++;
		}

		return size;
	}

	/**
	 * get a col sparse vector of a matrix
	 * 
	 * @param col
	 *            col id
	 * @return a sparse vector of {index, value}
	 * 
	 */
	public SparseVector column(int col) {

		SparseVector sv = new SparseVector(numRows);

		if (isCCSUsed) {
			for (int j = colPtr[col]; j < colPtr[col + 1]; j++) {
				int row = rowInd[j];
				double val = get(row, col);
				if (val != 0.0)
					sv.set(row, val);
			}
		} else {
			for (int row = 0; row < numRows; row++) {
				double val = get(row, col);
				if (val != 0.0)
					sv.set(row, val);
			}
		}

		return sv;
	}

	/**
	 * query the size of a specific col
	 * 
	 * @param col
	 *            col id
	 * @return the size of non-zero elements of a row
	 */
	public int columnSize(int col) {

		int size = 0;

		if (isCCSUsed) {
			for (int j = colPtr[col]; j < colPtr[col + 1]; j++) {
				int row = rowInd[j];
				double val = get(row, col);
				if (val != 0.0)
					size++;
			}
		} else {
			for (int row = 0; row < numRows; row++) {
				double val = get(row, col);
				if (val != 0.0)
					size++;
			}
		}

		return size;
	}

	/**
	 * @return sum of matrix data
	 */
	public double sum() {
		return Stats.sum(rowData);
	}

	/**
	 * @return mean of matrix data
	 */
	public double mean() {
		return sum() / size();
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(String.format("%d\t%d\t%d\n", new Object[] { numRows, numCols, size() }));

		for (MatrixEntry me : this)
			if (me.get() != 0)
				sb.append(String.format("%d\t%d\t%f\n", new Object[] { me.row() + 1, me.column() + 1, me.get() }));

		return sb.toString();
	}

	/**
	 * Finds the insertion index of CRS
	 */
	private int getCRSIndex(int row, int col) {
		int i = Arrays.binarySearch(colInd, rowPtr[row], rowPtr[row + 1], col);

		if (i >= 0 && colInd[i] == col)
			return i;
		else
			throw new IndexOutOfBoundsException("Entry (" + (row + 1) + ", " + (col + 1)
					+ ") is not in the matrix structure");
	}

	/**
	 * Finds the insertion index of CCS
	 */
	private int getCCSIndex(int row, int col) {
		int i = Arrays.binarySearch(rowInd, colPtr[col], colPtr[col + 1], row);

		if (i >= 0 && rowInd[i] == row)
			return i;
		else
			throw new IndexOutOfBoundsException("Entry (" + (row + 1) + ", " + (col + 1)
					+ ") is not in the matrix structure");
	}

	public Iterator<MatrixEntry> iterator() {
		return new MatrixIterator();
	}

	/**
	 * Entry of a compressed row matrix
	 */
	private class SparseMatrixEntry implements MatrixEntry {

		private int row, cursor;

		/**
		 * Updates the entry
		 */
		public void update(int row, int cursor) {
			this.row = row;
			this.cursor = cursor;
		}

		public int row() {
			return row;
		}

		public int column() {
			return colInd[cursor];
		}

		public double get() {
			return rowData[cursor];
		}

		public void set(double value) {
			rowData[cursor] = value;
		}
	}

	private class MatrixIterator implements Iterator<MatrixEntry> {

		private int row, cursor;

		private SparseMatrixEntry entry = new SparseMatrixEntry();

		public MatrixIterator() {
			// Find first non-empty row
			nextNonEmptyRow();
		}

		/**
		 * Locates the first non-empty row, starting at the current. After the
		 * new row has been found, the cursor is also updated
		 */
		private void nextNonEmptyRow() {
			while (row < numRows && rowPtr[row] == rowPtr[row + 1])
				row++;
			cursor = rowPtr[row];
		}

		public boolean hasNext() {
			return cursor < rowData.length;
		}

		public MatrixEntry next() {
			entry.update(row, cursor);

			// Next position is in the same row
			if (cursor < rowPtr[row + 1] - 1)
				cursor++;

			// Next position is at the following (non-empty) row
			else {
				row++;
				nextNonEmptyRow();
			}

			return entry;
		}

		public void remove() {
			entry.set(0);
		}

	}
}
