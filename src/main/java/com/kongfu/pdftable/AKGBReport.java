package com.kongfu.pdftable;

import java.io.IOException;
import java.util.List;

/**
 * @author chengyibin
 */
public class AKGBReport extends Report {

  public static void main(String[] args) throws IOException {
    AKGBReport akgb = new AKGBReport("./src/main/resources/爱康国宾.pdf");
    List<List<List<String>>> parse = akgb.parse();
    printRealTables(parse);
  }


  public AKGBReport(String pdfPath) {
    super(pdfPath);
  }

  @Override
  public boolean isStartTable(String line) {
    return line.contains("检查者：") || line.contains("操作者：") || line.contains("审核者：");
  }

  @Override
  public boolean isEndTable(String line) {
    return line.contains("小结") || line.contains("初步意见");
  }

  @Override
  public boolean isPageHeader(String line) {
    return line.contains("体检号:");
  }

  @Override
  public boolean isPageFooter(String line) {
    return line.contains("爱康国宾");
  }

  @Override
  public boolean isIgnoreLine(String line) {
    return line.startsWith("此检验结果仅对本次标本负责");
  }

  @Override
  public String getBegin2StartRow() {
    return "健康体检结果";
  }
}
