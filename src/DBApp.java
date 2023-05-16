import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Properties;

public class DBApp {
	int MaximumRowsCountinTablePage;
	int MaximumEntriesinOctreeNode;

	// TODO: modify writing to csv
	public void init() throws IOException {
		Properties prop = new Properties();
		FileInputStream fis = new FileInputStream("resources/DBApp.config");

		prop.load(fis);
		this.MaximumRowsCountinTablePage = Integer.parseInt(prop.getProperty("MaximumRowsCountinTablePage"));
		this.MaximumEntriesinOctreeNode = Integer.parseInt(prop.getProperty("MaximumEntriesinOctreeNode"));

		// File metadataFile = new File("resources/metadata.csv");
		// FileWriter outputFile = new FileWriter(metadataFile);
		// outputFile.append("TableName,ColumnName,ColumnType,ClusteringKey,IndexName,IndexType,min,max\n");
		// outputFile.close();

	}

	// CREATING TABLE
	public void createTable(String strTableName, String strClusteringKeyColumn,
			Hashtable<String, String> htblColNameType, Hashtable<String, String> htblColNameMin,
			Hashtable<String, String> htblColNameMax) throws DBAppException, IOException, ClassNotFoundException {

		if (tableExists(strTableName))
			throw new DBAppException("this table already exists");

		File metadataFile = new File("resources/metadata.csv");
		FileWriter outputFile = new FileWriter(metadataFile, true);

		String line, column, columnType, min, max, isClustering;
		Enumeration<String> e = htblColNameType.keys();

		while (e.hasMoreElements()) {
			column = e.nextElement();
			columnType = htblColNameType.get(column);
			isClustering = strClusteringKeyColumn == column ? "True" : "False";
			min = htblColNameMin.get(column);
			max = htblColNameMax.get(column);

			line = strTableName + "," + column + "," + columnType + "," + isClustering + "," + "null" + "," + "null"
					+ "," + min + "," + max + "\n";
			outputFile.append(line);
		}
		outputFile.close();

		File f1 = new File("resources/data/" + strTableName);
		f1.mkdirs();

		TableInfo tableInfo = new TableInfo();
		tableInfo.tableName = strTableName;
		tableInfo.clusteringKeyName = strClusteringKeyColumn;
		writeObject(strTableName + "Info" + ".class", tableInfo);

	}

	public boolean tableExists(String strTableName) throws ClassNotFoundException, IOException {
		try {
			readObject(strTableName + "Info" + ".class");
			return true;
		} catch (FileNotFoundException ex) {
			return false;
		}
	}

	// CREATING INDEX

	// INSERTING
	public void insertIntoTable(String strTableName, Hashtable<String, Object> htblColNameValue) throws Exception {
		int n = MaximumRowsCountinTablePage;
		TableInfo tableInfo = (TableInfo) readObject(strTableName + "Info" + ".class");
		String pageName;

		validateInsertInput(htblColNameValue, tableInfo);

		String clusteringKeyValue = htblColNameValue.get(tableInfo.clusteringKeyName) + "";

		tableInfo.clusteringKeyData.add((String) clusteringKeyValue);
		tableInfo.clusteringKeyData.sort(Comparator.naturalOrder());
		writeObject(strTableName + "Info" + ".class", tableInfo);
		System.out.println(tableInfo.clusteringKeyData);

		int index = tableInfo.clusteringKeyData.indexOf(clusteringKeyValue);

		// get the index of the page to load
		int pagePointer = index / n;
		System.out.println("index: " + index); // get REAL n
		System.out.println("pointer: " + pagePointer); // get REAL n

		Tuple tuple = new Tuple(htblColNameValue); // create the tuple with the input hashtable

		try {

			pageName = tableInfo.tablePages.get(pagePointer);

			Page page = (Page) readObject("resources/data/" + tableInfo.tableName + "/" + pageName + ".class"); // deserilaize
																												// the
																												// page
			System.out.println("no. of tuples(before): " + page.tuples.size());

			if (page.tuples.size() < n) {
				System.out.println("abl insert");

				insertTuple(pageName, tuple, page, tableInfo.clusteringKeyName, tableInfo, strTableName);

				System.out.println("ba3d insert");

			} else {
				shiftTuples(pageName, tuple, pagePointer, strTableName, tableInfo.clusteringKeyName);
				System.out.println("shifted successfully");
			}

		} catch (IndexOutOfBoundsException e) {
			System.out.println(e.getMessage());
			System.out.println("abl create new page");

			createNewPage(tuple, tableInfo, strTableName);
			System.out.println("ba3d create new page");

		}
		printPagesContent(tableInfo);
	}

	private void validateInsertInput(Hashtable<String, Object> htblColNameValue, TableInfo tableInfo)
			throws DBAppException, ParseException {

		String clusteringKeyValue = null;
		ArrayList<String[]> result = csvReader("resources/metadata.csv", tableInfo.tableName);
		if (htblColNameValue.size() < result.size())
			throw new DBAppException("missing columns");

		Enumeration<String> x = htblColNameValue.keys();
		while (x.hasMoreElements()) {
			String current = x.nextElement();
			boolean flag = false;
			for (int i = 0; i < result.size(); i++) {
				if (current.equals(result.get(i)[1])) {
					flag = true;
					Object temp = htblColNameValue.get(current);
					boolean cluster = Boolean.parseBoolean(result.get(i)[3]);

					System.out.println(temp);
					if (temp instanceof Integer) {
						if (!result.get(i)[2].equals("java.lang.Integer"))
							throw new DBAppException("incompatible data type");
						temp = (int) temp;
						int min = (Integer.parseInt(result.get(i)[6]));
						int max = (Integer.parseInt(result.get(i)[7]));
						if (cluster) {
							clusteringKeyValue = htblColNameValue.get(current) + "";
							// clusterKeyIndex = counter;

						}
						if ((int) temp < min || (int) temp > max) {
							throw new DBAppException("integer out of range");
						}
					} else if (temp instanceof Double) {
						System.out.println("temp class: " + temp.getClass());
						System.out.println(result.get(i)[2]);
						if (!result.get(i)[2].equals("java.lang.Double"))
							throw new DBAppException("incompatible data type");
						temp = (double) temp;
						double min = (Double.parseDouble(result.get(i)[6]));
						double max = (Double.parseDouble(result.get(i)[7]));
						if (cluster) {
							clusteringKeyValue = htblColNameValue.get(current) + "";
							// clusterKeyIndex = counter;
						}
						if ((double) temp < min || (double) temp > max) {
							throw new DBAppException("double out of range");
						}
					} else if (temp instanceof String) {
						if (!result.get(i)[2].equals("java.lang.String"))
							throw new DBAppException("incompatible data type");
						temp = (String) temp;
						String min = result.get(i)[6];
						String max = result.get(i)[7];
						if (cluster) {
							clusteringKeyValue = htblColNameValue.get(current) + "";
							// clusterKeyIndex = counter;
						}
						if (((String) temp).compareTo(min) < 0 || ((String) temp).compareTo(max) > 0) {
							throw new DBAppException("string out of range");
						}
					} else if (temp instanceof Date) {
						if (!result.get(i)[2].equals("java.util.Date"))
							throw new DBAppException("incompatible data type");
						temp = (Date) temp;
						Date min = new SimpleDateFormat("YYYY-MM-DD").parse(result.get(i)[6]);
						Date max = new SimpleDateFormat("YYYY-MM-DD").parse(result.get(i)[7]);
						if (cluster) {
							SimpleDateFormat format = new SimpleDateFormat("YYYY-MM-DD");
							Date d = format.parse(htblColNameValue.get(current) + "");
							String date = format.format(d);
							clusteringKeyValue = date + "";
							// clusterKeyIndex = counter;

						}
						if (((Date) temp).compareTo(min) < 0 || ((Date) temp).compareTo(max) > 0) {
							throw new DBAppException("date out of range");
						}
					} else
						throw new DBAppException("this type is not supported");

				}

			}
			if (!flag)
				throw new DBAppException("you used a column that does not exist");
			// counter++;
		}
		if (clusteringKeyValue == null)
			throw new DBAppException("clustering key cannot be null");
	}

	private void createNewPage(Tuple tuple, TableInfo tableInfo, String strTableName) throws IOException {
		tableInfo.pageCount++;
		tableInfo.tablePages.add(strTableName + "_p" + tableInfo.pageCount);
		String pageName = strTableName + "_p" + tableInfo.pageCount;
		Page p = new Page(); // create page to add the tuple then serialize it
		p.tuples.add(tuple); // add the tuple to the tuples array in the page
		writeObject("resources/data/" + tableInfo.tableName + "/" + pageName + ".class", p); // serialize
		writeObject(strTableName + "Info" + ".class", tableInfo);
		System.out.println("no. of tuples(after): " + p.tuples.size());
	}

	private void shiftTuples(String pageName, Tuple tuple, int pagePointer, String strTableName, String clusterKeyName)
			throws ClassNotFoundException, IOException {
		int n = MaximumRowsCountinTablePage; // read from properties
		TableInfo tableInfo = (TableInfo) readObject(strTableName + "Info" + ".class");
		Page page = (Page) readObject("resources/data/" + tableInfo.tableName + "/" + pageName + ".class"); // deserialize
																											// destination
																											// page
		if (page.tuples.size() < n) {
			insertTuple(pageName, tuple, page, clusterKeyName, tableInfo, strTableName);
			System.out.println("returned from shift");
			return;
		}

		Tuple tempTuple = page.tuples.get(n - 1);
		page.tuples.remove(n - 1);
		insertTuple(pageName, tuple, page, clusterKeyName, tableInfo, strTableName);

		try {
			pageName = tableInfo.tablePages.get(++pagePointer);
			System.out.println("lol 1");
			shiftTuples(pageName, tempTuple, pagePointer, strTableName, clusterKeyName);
			System.out.println("lol 2");

		} catch (IndexOutOfBoundsException e) {
			System.out.println(e.getMessage());
			System.out.println("lol 3");

			createNewPage(tempTuple, tableInfo, strTableName);
			System.out.println("lol 4");

		}

	}

	private void insertTuple(String pageName, Tuple tuple, Page page, String clusteringKeyName, TableInfo tableInfo,
			String strTableName)
			throws ClassNotFoundException, IOException {

		System.out.println("da5al insert");
		Comparable clusterValue = (Comparable) tuple.data.get(clusteringKeyName);

		// binary search on tuples
		int left = 0;
		int right = page.tuples.size() - 1;

		while (left <= right) {
			int mid = left + (right - left) / 2;
			Tuple temp = page.tuples.get(mid);
			Comparable currentCluster = (Comparable) temp.data.get(clusteringKeyName);

			if (left == right) {
				if (currentCluster.compareTo(clusterValue) < 0)
					page.tuples.insertElementAt(tuple, left + 1);
				else
					page.tuples.insertElementAt(tuple, left);
				break;
			}

			if (currentCluster.compareTo(clusterValue) < 0)
				left = mid + 1;
			else
				right = mid - 1;
		}

		writeObject(strTableName + "Info" + ".class", tableInfo);
		writeObject("resources/data/" + tableInfo.tableName + "/" + pageName + ".class", page);
		System.out.println("no. of tuples(after): " + page.tuples.size());
		System.out.println("5arag insert");

	}

	// UPDATING
	public void updateTable(String strTableName, String strClusteringKeyValue,
			Hashtable<String, Object> htblColNameValue)
			throws DBAppException, ClassNotFoundException, IOException, ParseException {

		int n = MaximumRowsCountinTablePage; // GET ACTUAL N
		TableInfo tableInfo = (TableInfo) readObject(strTableName + "Info" + ".class");
		ArrayList<String> clusteringKeyData = tableInfo.clusteringKeyData;

		validateUpdateInput(htblColNameValue, tableInfo);

		int clusterIndex = Collections.binarySearch(clusteringKeyData, strClusteringKeyValue);
		if (clusterIndex == -1)
			throw new DBAppException("tuple not found");
		System.out.println("cluster index: " + clusterIndex);
		int pagePointer = clusterIndex / n;

		System.out.println("clustering key name: " + tableInfo.clusteringKeyName);

		String pageName = strTableName + "_p" + pagePointer; // assume page pointer is equivalent to pageCount

		System.out.println("pageName: " + pageName);

		Page page = (Page) readObject("resources/data/" + tableInfo.tableName + "/" + pageName + ".class"); // deserialize
																											// required
																											// page

		// method for matching the right tuple with the strClusteringKeyValue and
		// replacing its data
		for (int i = 0; i < page.tuples.size(); i++) {
			Tuple t = page.tuples.get(i);
			System.out.println("clusterKeyValue: " + t.data.get(tableInfo.clusteringKeyName));
			System.out.println("condition: " + t.data.get(tableInfo.clusteringKeyName).equals(strClusteringKeyValue));
			if (t.data.get(tableInfo.clusteringKeyName).equals(Integer.parseInt(strClusteringKeyValue))) {
				System.out.println("da5l if");
				updateTuple(t, htblColNameValue);
			}
		}

		writeObject("resources/data/" + tableInfo.tableName + "/" + pageName + ".class", page); // serialize back
	}

	private void validateUpdateInput(Hashtable<String, Object> htblColNameValue, TableInfo tableInfo)
			throws DBAppException, ParseException {

		ArrayList<String[]> result = csvReader("resources/metadata.csv", tableInfo.tableName);

		Enumeration<String> x = htblColNameValue.keys();
		while (x.hasMoreElements()) {
			String current = x.nextElement();
			boolean flag = false;
			for (int i = 0; i < result.size(); i++) {
				if (current.equals(result.get(i)[1])) {
					flag = true;
					Object temp = htblColNameValue.get(current);

					System.out.println(temp);
					if (temp instanceof Integer) {
						if (!result.get(i)[2].equals("java.lang.Integer"))
							throw new DBAppException("incompatible data type");
						temp = (int) temp;
						int min = (Integer.parseInt(result.get(i)[6]));
						int max = (Integer.parseInt(result.get(i)[7]));
						if ((int) temp < min || (int) temp > max) {
							throw new DBAppException("integer out of range");
						}
					} else if (temp instanceof Double) {
						System.out.println("temp class: " + temp.getClass());
						System.out.println(result.get(i)[2]);
						if (!result.get(i)[2].equals("java.lang.Double"))
							throw new DBAppException("incompatible data type");
						temp = (double) temp;
						double min = (Double.parseDouble(result.get(i)[6]));
						double max = (Double.parseDouble(result.get(i)[7]));
						if ((double) temp < min || (double) temp > max) {
							throw new DBAppException("double out of range");
						}
					} else if (temp instanceof String) {
						if (!result.get(i)[2].equals("java.lang.String"))
							throw new DBAppException("incompatible data type");
						temp = (String) temp;
						String min = result.get(i)[6];
						String max = result.get(i)[7];
						if (((String) temp).compareTo(min) < 0 || ((String) temp).compareTo(max) > 0) {
							throw new DBAppException("string out of range");
						}
					} else if (temp instanceof Date) {
						if (!result.get(i)[2].equals("java.util.Date"))
							throw new DBAppException("incompatible data type");
						temp = (Date) temp;
						Date min = new SimpleDateFormat("YYYY-MM-DD").parse(result.get(i)[6]);
						Date max = new SimpleDateFormat("YYYY-MM-DD").parse(result.get(i)[7]);
						if (((Date) temp).compareTo(min) < 0 || ((Date) temp).compareTo(max) > 0) {
							throw new DBAppException("date out of range");
						}
					} else
						throw new DBAppException("this type is not supported");

				}

			}
			if (!flag)
				throw new DBAppException("you used a column that does not exist");
			// counter++;
		}
	}

	private void updateTuple(Tuple t, Hashtable<String, Object> htblColNameValue) {
		Enumeration<String> e = htblColNameValue.keys();
		while (e.hasMoreElements()) {
			String key = e.nextElement();
			Object value = htblColNameValue.get(key);
			if (t.data.containsKey(key))
				System.out.println("containsKey: " + t.data.containsKey(key));
			t.data.replace(key, value);
		}

	}

	// DELETING
	public void deleteFromTable(String strTableName,
			Hashtable<String, Object> htblColNameValue) throws DBAppException, ClassNotFoundException, IOException {

		TableInfo tableInfo = (TableInfo) readObject(strTableName + "Info" + ".class");

		deleteTuples(strTableName, tableInfo, htblColNameValue);

		System.out.println("\nafter tuples deletion");
		printPagesContent(tableInfo);

		writeObject(strTableName + "Info" + ".class", tableInfo);

		tableInfo = (TableInfo) readObject(strTableName + "Info" + ".class");
		orderTuples(tableInfo);
		writeObject(strTableName + "Info" + ".class", tableInfo);

		System.out.println("\nafter ordering tuples");
		printPagesContent(tableInfo);

		tableInfo = (TableInfo) readObject(strTableName + "Info" + ".class");
		deleteEmptyPages(strTableName, tableInfo);

		writeObject(strTableName + "Info" + ".class", tableInfo);

		System.out.println("\nafter pages deletion");
		printPagesContent(tableInfo);

	}

	private boolean checkTupleValues(Tuple tuple, Hashtable<String, Object> htblColNameValue) {
		boolean flag = true;
		Enumeration<String> e = htblColNameValue.keys();
		while (e.hasMoreElements()) {
			String key = e.nextElement();
			Object value = htblColNameValue.get(key);
			Object tupleValue = tuple.data.get(key);
			if (!value.equals(tupleValue))
				flag = false;
		}
		return flag;
	}

	private void deleteTuples(String strTableName, TableInfo tableInfo, Hashtable<String, Object> htblColNameValue)
			throws ClassNotFoundException, IOException {
		for (int i = 0; i < tableInfo.tablePages.size(); i++) {
			Page page = (Page) readObject(
					"resources/data/" + tableInfo.tableName + "/" + tableInfo.tablePages.get(i) + ".class");
			// int tuplesToDelete = 0;
			for (int j = 0; j < page.tuples.size(); j++) {
				Tuple tuple = page.tuples.get(j);
				Object clusteringKeyValue = tuple.data.get(tableInfo.clusteringKeyName);
				if (checkTupleValues(tuple, htblColNameValue)) {
					// tuplesToDelete++;
					System.out.print("tuple=> " + tableInfo.clusteringKeyName + ": " + clusteringKeyValue + "\n");
					page.tuples.remove(tuple);
					tableInfo.clusteringKeyData.remove(clusteringKeyValue);
					j--;
				}
			}
			writeObject("resources/data/" + tableInfo.tableName + "/" + tableInfo.tablePages.get(i) + ".class", page);
		}
		writeObject(strTableName + "Info" + ".class", tableInfo);
	}

	private void orderTuples(TableInfo tableInfo) throws ClassNotFoundException, IOException {
		int n = MaximumRowsCountinTablePage; // get real max from config
		int pageNotFullIndex = 0;
		ArrayList<String> tablePages = tableInfo.tablePages;
		Page pageNotFull = null;

		// reach the first page that is not full
		for (int i = 0; i < tablePages.size(); i++) {
			Page page = (Page) readObject("resources/data/" + tableInfo.tableName + "/" + tablePages.get(i) + ".class");
			if (page.tuples.size() < n) {
				pageNotFull = page;
				pageNotFullIndex = i;
				writeObject("resources/data/" + tableInfo.tableName + "/" + tableInfo.tablePages.get(i) + ".class",
						page);
				break;
			}
			writeObject("resources/data/" + tableInfo.tableName + "/" + tableInfo.tablePages.get(i) + ".class", page);
		}

		// base case

		if (pageNotFull == null)
			return;

		// base case

		if (tablePages.get(tablePages.size() - 1).equals(tablePages.get(pageNotFullIndex)))
			return;

		int missingTuples = n - pageNotFull.tuples.size();
		ArrayList<Tuple> tempTuples = new ArrayList<Tuple>();

		int nextPageIndex = tablePages.indexOf(tablePages.get(pageNotFullIndex + 1));
		for (int i = nextPageIndex; i < tablePages.size(); i++) {
			Page page = (Page) readObject("resources/data/" + tableInfo.tableName + "/" + tablePages.get(i) + ".class");
			while (tempTuples.size() < missingTuples) {
				if (page.tuples.isEmpty())
					break;
				tempTuples.add(page.tuples.remove(0));

			}
			if (tempTuples.size() == missingTuples) {
				writeObject("resources/data/" + tableInfo.tableName + "/" + tableInfo.tablePages.get(i) + ".class",
						page);
				break;
			}

			writeObject("resources/data/" + tableInfo.tableName + "/" + tableInfo.tablePages.get(i) + ".class", page);
		}

		// base case
		if (tempTuples.isEmpty())
			return;

		// insert the tuples from tempTuples to pageNotFull
		int tempTupleSize = tempTuples.size();
		pageNotFull = (Page) readObject(
				"resources/data/" + tableInfo.tableName + "/" + tableInfo.tablePages.get(pageNotFullIndex) + ".class");

		for (int i = 0; i < tempTupleSize; i++)
			pageNotFull.tuples.add(tempTuples.remove(i));

		writeObject(
				"resources/data/" + tableInfo.tableName + "/" + tableInfo.tablePages.get(pageNotFullIndex) + ".class",
				pageNotFull);

		orderTuples(tableInfo);
	}

	private void deleteEmptyPages(String strTableName, TableInfo tableInfo) throws ClassNotFoundException, IOException {
		ArrayList<String> tablePages = tableInfo.tablePages;
		
		for (int i = 0; i < tablePages.size(); i++) {
			Page page = (Page) readObject("resources/data/" + tableInfo.tableName + "/" + tablePages.get(i) + ".class");
			if (page.tuples.isEmpty()) {
				// pagesToDelete++;
				File pageFile = new File(
						"resources/data/" + tableInfo.tableName + "/" + tableInfo.tablePages.get(i) + ".class");
				pageFile.delete();
				tableInfo.tablePages.remove(tableInfo.tablePages.get(i));
				i--;
			} else
				writeObject("resources/data/" + tableInfo.tableName + "/" + tableInfo.tablePages.get(i) + ".class",
						page);
		}
		writeObject(strTableName + "Info" + ".class", tableInfo);

	}
	
	public void createIndex(String strTableName, String[] strarrColName) throws DBAppException, IOException, ClassNotFoundException {
		if (!tableExists(strTableName))
			throw new DBAppException("Table does not exist");
		//change target columns in metadata.csv
		//et2aked mel tarteeb el sah
		OctTreeIndex t = new OctTreeIndex(strTableName,"curretnIndex", strarrColName);
	
	}

	// SELECTING
	public Iterator selectFromTable(SQLTerm[] arrSQLTerms, String[] strarrOperators) throws DBAppException, ClassNotFoundException, IOException {
	//	ResultSet resultSet = new ResultSet();
		ResultSet tempResultSet = new ResultSet();
		ResultSet orResultSet = new ResultSet();
		ResultSet allResultSet = new ResultSet();
		boolean useIndex = false;
		
		allResultSet = createAllResultSet(arrSQLTerms[0]._strTableName);
		ArrayList<String[]> tableData = csvReader("metadata.csv", arrSQLTerms[0]._strTableName);
		//CHECK IF 3 COLUMNS ARE OF SAME INDEX
		for(int i=0;i<arrSQLTerms.length;i++){
			for(int j = 0; j<tableData.size(); j++) {
				if(arrSQLTerms[i]._strColumnName.equals(tableData.get(j)[1])){
					if(tableData.get(j)[4]!=null){
						//selectFromIndex();
						useIndex = true;
					}
					else {
						//selectLinearWithIndex(arrSQLTerms[i]._strTableName, arrSQLTerms[i]._strColumnName, arrSQLTerms[i]._objValue);
					}
				}
			}
			
		}
		if(useIndex) {
			//selecctUsingIndex();
			//desrialize index in an octTreeIndex object
			//get octTree from octTree
			//octTree.findTupleRefernce();
			//
		}
		if(!useIndex) {
			//instead of for loop, check the strarrOperators (or, and) to know which result set to continue on
			tempResultSet = selectLinear(arrSQLTerms[0], arrSQLTerms[0]._strTableName);
			

			if(strarrOperators.length!=0) {
				for(int i = 0; i<strarrOperators.length; i++) {
					if(strarrOperators[i].equals("AND")) {
						tempResultSet = selectFromResultSet(tempResultSet, arrSQLTerms[i+1]);	
						
					}
					
					if(strarrOperators[i].equals("OR")) {
						orResultSet = selectLinear(arrSQLTerms[i+1], arrSQLTerms[i+1]._strTableName);
						tempResultSet.tuples.addAll(orResultSet.tuples);
						tempResultSet = removeDuplicates(tempResultSet);

					}

					if(strarrOperators[i].equals("XOR")) {
						ResultSet notFirstQuery = new ResultSet();
						ResultSet temppp = new ResultSet();
						//ResultSet secondResultSet = new ResultSet();
						//secondResultSet = selectLinear(arrSQLTerms[i+1], arrSQLTerms[i+1]._strTableName);
						notFirstQuery = getSetDifference(allResultSet, tempResultSet);
						temppp.tuples = selectFromResultSet(notFirstQuery, arrSQLTerms[i+1]).tuples;
						String oppositeOperator = getOpposite(arrSQLTerms[i+1]._strOperator);
						SQLTerm sqlTerm = new SQLTerm(arrSQLTerms[i+1]._strTableName, arrSQLTerms[i+1]._strColumnName, oppositeOperator, arrSQLTerms[i+1]._objValue);
						temppp.tuples.addAll(selectFromResultSet(tempResultSet, sqlTerm).tuples);
						
						tempResultSet.tuples=temppp.tuples;
						//=notFirstQuery;

						//notSecondQuery = getSetDifference(allResultSet, secondResultSet);
						
						//tempResultSet=getSimilar(tempResultSet,notSecondQuery);
						//tempResultSet=getSimilar(notFirstQuery,secondResultSet);
						//return temppp;

					}
					
				}
			}
			//resultSet.tuples.addAll(tempResultSet.tuples);  //check TOMORROW ELZA2 F TEEZ BA3D
		}
		return tempResultSet;
	}
	
	public String getOpposite(String operator) {
		 if(operator.equals("=")) {
			 return "!=";
		 }
		 if(operator.equals("!=")) {
			 return "=";
		 }
		 if(operator.equals(">")) {
			 return "<=";
		 }
		 if(operator.equals("<")) {
			 return ">=";
		 }
		 if(operator.equals(">=")) {
			 return "<";
		 }
		 if(operator.equals("<=")) {
			 return ">";
		 }
		return "";
	}


	 public static ResultSet getSetDifference(ResultSet allResultSet, ResultSet tempResultSet) {
			ResultSet res = new ResultSet();
			res = allResultSet;
			for(int i = 0; i<res.tuples.size(); i++) {
				for(int j = 0; j<tempResultSet.tuples.size(); j++) {
					if(res.tuples.get(i).data.equals(tempResultSet.tuples.get(j).data)) {
						//System.out.println("66666666666");
						res.tuples.remove(res.tuples.get(i));		
					}
				}			
			}
			return res;
		}

	public ResultSet createAllResultSet(String tableName) throws ClassNotFoundException, IOException {
		ResultSet resultSet = new ResultSet();
		TableInfo tableInfo = (TableInfo) readObject(tableName + "Info" + ".class");
		ArrayList<String> tablePages = tableInfo.tablePages;
		for(int i = 0; i<tablePages.size(); i++) {
			String pageName = tablePages.get(i);
			Page page = (Page) readObject("resources/data/" + tableInfo.tableName + "/" + pageName + ".class");
			for (int j = 0; j < page.tuples.size(); j++) {
				Tuple currentTuple = page.tuples.get(j);
				resultSet.tuples.add(currentTuple);
			}
			writeObject("resources/data/" + tableInfo.tableName + "/" + tableInfo.tablePages.get(i) + ".class", page);
		}
		writeObject(tableName + "Info" + ".class", tableInfo);
		return resultSet;
	}

	public ResultSet removeDuplicates(ResultSet resultSet) {
		ResultSet res = new ResultSet();
		res = resultSet;
        for (int i = 0; i < res.tuples.size(); i++) {
            Tuple tup = res.tuples.get(i);

            ArrayList<String> keys = new ArrayList<String>();
            ArrayList<Object> values = new ArrayList<Object>();
            keys.addAll(tup.data.keySet());
            values.addAll(tup.data.values());

            for (int j = 0; j < res.tuples.size(); j++) {
                Tuple compareTup = res.tuples.get(j);
                ArrayList<String> compKeys = new ArrayList<String>();
                ArrayList<Object> compValues = new ArrayList<Object>();
                compKeys.addAll(compareTup.data.keySet());
                compValues.addAll(compareTup.data.values());

                if (keys.equals(compKeys) && values.equals(compValues) && i != j) {
                    res.tuples.remove(i);

                }
            }
        }
        return res;

    }

	public ResultSet selectLinear(SQLTerm query, String tableName) throws ClassNotFoundException, IOException {
		String columnName = query._strColumnName;
		String operator = query._strOperator;
		Object objValue = query._objValue;
		ResultSet resultSet = new ResultSet();
		TableInfo tableInfo = (TableInfo) readObject(tableName + "Info" + ".class");
		ArrayList<String> tablePages = tableInfo.tablePages;
		for(int i = 0; i<tablePages.size(); i++) {
			String pageName = tablePages.get(i);
			Page page = (Page) readObject("resources/data/" + tableInfo.tableName + "/" + pageName + ".class");
			for (int j = 0; j < page.tuples.size(); j++) {
				Tuple currentTuple = page.tuples.get(j);
				Enumeration<String> e = currentTuple.data.keys();
				while (e.hasMoreElements()) {
					String key = e.nextElement();
					if(key.equals(columnName)) {
						if(operator.equals("="))
						{
							if(equal(currentTuple.data.get(key), objValue))
								resultSet.tuples.add(currentTuple);
						}
						if(operator.equals("!="))
						{
							if(notEqual(currentTuple.data.get(key), objValue))
								resultSet.tuples.add(currentTuple);
							
						}
						if(operator.equals("<"))
						{
							if(lessThan(currentTuple.data.get(key), objValue))
								resultSet.tuples.add(currentTuple);
						}
						if(operator.equals("<="))
						{
							if(lessThanOrEqual(currentTuple.data.get(key), objValue))
								resultSet.tuples.add(currentTuple);	
						}
						if(operator.equals(">"))
						{
							if(greaterThan(currentTuple.data.get(key), objValue))
								resultSet.tuples.add(currentTuple);	
						}	
						if(operator.equals(">="))
						{
							if(greaterThanOrEqual(currentTuple.data.get(key), objValue))
								resultSet.tuples.add(currentTuple);	
						}
						
					}
				
				}

			}
			writeObject("resources/data/" + tableInfo.tableName + "/" + tableInfo.tablePages.get(i) + ".class", page);
		}
		
		writeObject(tableName + "Info" + ".class", tableInfo);
		return resultSet;
	}
	public ResultSet selectFromResultSet( ResultSet tempResultSet, SQLTerm query ) {
		ResultSet resultSet = new ResultSet();
		
		String columnName = query._strColumnName;
		String operator = query._strOperator;
		Object objValue = query._objValue;
		
		for(int i=0;i<tempResultSet.tuples.size();i++)
		{
			
			if(operator.equals("="))
			{
				if(equal(tempResultSet.tuples.get(i).data.get(columnName), objValue))
					resultSet.tuples.add(tempResultSet.tuples.get(i));
			}
			if(operator.equals("!="))
			{
				if(notEqual(tempResultSet.tuples.get(i).data.get(columnName), objValue))
					resultSet.tuples.add(tempResultSet.tuples.get(i));
				
			}
			if(operator.equals("<"))
			{
				if(lessThan(tempResultSet.tuples.get(i).data.get(columnName), objValue))
					resultSet.tuples.add(tempResultSet.tuples.get(i));
			}
			if(operator.equals("<="))
			{
				if(lessThanOrEqual(tempResultSet.tuples.get(i).data.get(columnName), objValue))
					resultSet.tuples.add(tempResultSet.tuples.get(i));	
			}
			if(operator.equals(">"))
			{
				if(greaterThan(tempResultSet.tuples.get(i).data.get(columnName), objValue))
					resultSet.tuples.add(tempResultSet.tuples.get(i));	
			}	
			if(operator.equals(">="))
			{
				if(greaterThanOrEqual(tempResultSet.tuples.get(i).data.get(columnName), objValue))
					resultSet.tuples.add(tempResultSet.tuples.get(i));	
			}
			
		}
		
		return resultSet;
		
	}
	//COMPARE TO
	public boolean lessThan(Object tupleVal, Object objVal) {
        if (tupleVal instanceof Integer) {
            if ((Integer) tupleVal < (Integer) objVal)
               return true;
            else {
                return false;
            }

        } else if (tupleVal instanceof Double) {
            if ((Double) tupleVal < (Double) objVal)
                return true;
            else{
                return false;
            }
        } else if (tupleVal instanceof String) {
            ((String) tupleVal).toLowerCase();
            ((String) objVal).toLowerCase();
            if (((String) tupleVal).compareTo((String) objVal) < 0)
               return true;
            else {
                return false;
            }


        } else if(tupleVal instanceof Date) {
            if (((Date) tupleVal).compareTo((Date) objVal) < 0)
                return true;
            else {
                return false; 
            }
        }
        return true;

      }
	public boolean lessThanOrEqual(Object tupleVal, Object objVal) {
	        if (tupleVal instanceof Integer) {
	            if ((Integer) tupleVal <= (Integer) objVal)
	               return true;
	            else {
	                return false;
	            }
	
	        } else if (tupleVal instanceof Double) {
	            if ((Double) tupleVal <= (Double) objVal)
	                return true;
	            else{
	                return false;
	            }
	        } else if (tupleVal instanceof String) {
	            ((String) tupleVal).toLowerCase();
	            ((String) objVal).toLowerCase();
	            if (((String) tupleVal).compareTo((String) objVal) <= 0)
	               return true;
	            else {
	                return false;
	            }
	
	
	        } else if(tupleVal instanceof Date) {
	            if (((Date) tupleVal).compareTo((Date) objVal) <= 0)
	                return true;
	            else {
	                return false; 
	            }
	
	        }
            return true;

	}
	
	public boolean greaterThanOrEqual(Object tupleVal, Object objVal) {
	            if (tupleVal instanceof Integer) {
	                if ((Integer) tupleVal >= (Integer) objVal)
	                   return true;
	                else {
	                    return false;
	                }
	
	            } else if (tupleVal instanceof Double) {
	                if ((Double) tupleVal >= (Double) objVal)
	                    return true;
	                else{
	                    return false;
	                }
	            } else if (tupleVal instanceof String) {
	                ((String) tupleVal).toLowerCase();
	                ((String) objVal).toLowerCase();
	                if (((String) tupleVal).compareTo((String) objVal) >= 0)
	                   return true;
	                else {
	                    return false;
	                }
	
	
	            } else if(tupleVal instanceof Date) {
	                if (((Date) tupleVal).compareTo((Date) objVal) >= 0)
	                    return true;
	                else {
	                    return false; 
	                }
	
	            }
	            return true;

	        }
	
	public boolean greaterThan(Object tupleVal, Object objVal) {
	            if (tupleVal instanceof Integer) {
	                if ((Integer) tupleVal > (Integer) objVal)
	                   return true;
	                else {
	                    return false;
	                }
	
	            } else if (tupleVal instanceof Double) {
	                if ((Double) tupleVal > (Double) objVal)
	                    return true;
	                else{
	                    return false;
	                }
	            } else if (tupleVal instanceof String) {
	                ((String) tupleVal).toLowerCase();
	                ((String) objVal).toLowerCase();
	                if (((String) tupleVal).compareTo((String) objVal) > 0)
	                   return true;
	                else {
	                    return false;
	                }
	
	
	            } else if(tupleVal instanceof Date) {
	                if (((Date) tupleVal).compareTo((Date) objVal) > 0)
	                    return true;
	                else {
	                    return false; 
	                }
	
	            }
	            return true;

	        }
	public boolean notEqual(Object tupleVal, Object objVal) {
	            if (tupleVal instanceof Integer) {
	                if (!((Integer) tupleVal).equals((Integer) objVal))
	                   return true;
	                else {
	                    return false;
	                }
	
	            } else if (tupleVal instanceof Double) {

	                if (!((Double) tupleVal).equals((Double) objVal)) {
	                	return true;

	                }
	                else{
	                    return false;
	                }
	            } else if (tupleVal instanceof String) {
	                ((String) tupleVal).toLowerCase();
	                ((String) objVal).toLowerCase();
	                if (((String) tupleVal).compareTo((String) objVal) != 0)
	                   return true;
	                else {
	                    return false;
	                }
	
	
	            } else if(tupleVal instanceof Date) {
	                if (((Date) tupleVal).compareTo((Date) objVal) != 0)
	                    return true;
	                else {
	                    return false; 
	                }
	
	            }
	            return true;

	        }
	public boolean equal(Object tupleVal, Object objVal) {
	            if (tupleVal instanceof Integer) {
	                if (((Integer) tupleVal).equals((Integer) objVal))
	                   return true;
	                else {
	                    return false;
	                }
	
	            } else if (tupleVal instanceof Double) {
	                if (((Double) tupleVal).equals((Double) objVal))
	                    return true;
	                else{
	                    return false;
	                }
	            } else if (tupleVal instanceof String) {
	                ((String) tupleVal).toLowerCase();
	                ((String) objVal).toLowerCase();
	                if (((String) tupleVal).compareTo((String) objVal) == 0)
	                   return true;
	                else {
	                    return false;
	                }
	
	
	            } else if(tupleVal instanceof Date) {
	                if (((Date) tupleVal).compareTo((Date) objVal) == 0)
	                    return true;
	                else {
	                    return false; 
	                }
	
	            }
	            return true;
	        }

	// HELPER METHODS
	public ArrayList<String[]> csvReader(String fileName, String strTableName) {
		String csvFile = fileName;
		String line = "";
		String csvSeparator = ",";
		ArrayList<String[]> result = new ArrayList<String[]>();
		try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
			br.readLine();
			while ((line = br.readLine()) != null) {
				String[] values = line.split(csvSeparator);
				if (values[0].equals(strTableName)) {
					result.add(values);
				}

			}
			return result;

		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}

	}
	
	public Object readObject(String path) throws IOException, ClassNotFoundException {
		FileInputStream fileIn = new FileInputStream(path);
		ObjectInputStream in = new ObjectInputStream(fileIn);
		Object o = (Object) in.readObject();
		in.close();
		fileIn.close();
		return o;
	}

	public void writeObject(String path, Object obj) throws IOException {
		FileOutputStream fileOut = new FileOutputStream(path);
		ObjectOutputStream out = new ObjectOutputStream(fileOut);
		out.writeObject(obj);
		out.close();
		fileOut.close();
	}

	public void printPagesContent(TableInfo tableInfo) throws ClassNotFoundException, IOException {

		System.out.println("\n--------------------");
		System.out.println(tableInfo.tableName + " Table:");
		System.out.println("--------------------");

		for (int i = 0; i < tableInfo.tablePages.size(); i++) {
			String pageName = tableInfo.tablePages.get(i);
			System.out.print(tableInfo.tableName + "_p" + i + ": " + "\n{");
			Page page = (Page) readObject("resources/data/" + tableInfo.tableName + "/" + pageName + ".class");
			for (int j = 0; j < page.tuples.size(); j++) {
				System.out.print("\n  < ");
				Tuple curTuple = page.tuples.get(j);
				Enumeration<String> e = curTuple.data.keys();
				while (e.hasMoreElements()) {
					String key = e.nextElement();
					System.out.print(key + ": " + curTuple.data.get(key));
					if (e.hasMoreElements())
						System.out.print(" , ");
				}

				if (j == page.tuples.size() - 1)
					System.out.print(" >\n");
				else
					System.out.print(" >");

			}
			if (i == tableInfo.tablePages.size() - 1)
				System.out.println("}");
			else
				System.out.println("}\n");
			writeObject("resources/data/" + tableInfo.tableName + "/" + tableInfo.tablePages.get(i) + ".class", page);
		}
		System.out.println("--------------------\n");

	}

	public void clear() throws IOException {
		File dataFolder = new File("resources/data");

		for (File folder : dataFolder.listFiles()) {
			for (File file : folder.listFiles())
				file.delete();
			folder.delete();
		}

		File infoFile = new File("StudentInfo.class");
		infoFile.delete();

		File metadataFile = new File("resources/metadata.csv");
		FileWriter outputFile = new FileWriter(metadataFile);
		outputFile.append("TableName,ColumnName,ColumnType,ClusteringKey,IndexName,IndexType,min,max\n");
		outputFile.close();

	}
}