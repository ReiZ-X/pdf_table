package com.kongfu.pdftable;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author chengyibin
 */
public class MNJKReport extends Report {

  public static void main(String[] args) throws IOException {
    MNJKReport mnjkReport = new MNJKReport("./src/main/resources/美年健康.pdf");
    List<List<List<String>>> parse = mnjkReport.parse();
    printRealTables(parse);
  }

  public MNJKReport(String pdfPath) {
    super(pdfPath);
  }

  @Override
  public boolean isStartTable(String line) {
    return line.contains("医生：");
  }

  @Override
  public boolean isEndTable(String line) {
    return line.contains("小结") || line.contains("检查结果");
  }

  @Override
  public boolean isPageHeader(String line) {
    return false;
  }

  @Override
  public boolean isPageFooter(String line) {
    return false;
  }

  @Override
  public boolean isIgnoreLine(String line) {
    return false;
  }

  @Override
  public String getBegin2StartRow() {
    return null;
  }

  @Override
  public int getColOffset() {
    return 2;
  }
}
