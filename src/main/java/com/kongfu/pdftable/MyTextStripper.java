package com.kongfu.pdftable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/**
 * @author chengyibin
 */
@Slf4j
public class MyTextStripper extends PDFTextStripper {

  //这里使用静态变量，用局部变量的话会取不到值，TODO
  private static List<List<TextPosition>> pages = new ArrayList<>();
  private static ReentrantLock lock = new ReentrantLock();

  @Override
  public void writePage() throws IOException {
    final List<List<TextPosition>> pageText = getCharactersByArticle();
    List<TextPosition> list = new ArrayList<>();
    list.addAll(pageText.get(0));
    pages.add(list);
    super.writePage();
  }

  public List<List<TextPosition>> parsePdfTable(String pdfPath) {
    try {
      lock.lock();
      PDDocument doc = PDDocument.load(new File(pdfPath));
      MyTextStripper textStripper = new MyTextStripper();
      textStripper.getText(doc);
      doc.close();
      List<List<TextPosition>> result = new ArrayList<>();
      result.addAll(pages);
      pages.clear();
      return result;
    } catch (Exception e) {
      log.error("", e);
    } finally {
      lock.unlock();
    }
    return new ArrayList<>();
  }

  /**
   * Instantiate a new PDFTextStripper object.
   *
   * @throws IOException If there is an error loading the properties.
   */
  public MyTextStripper() throws IOException {
  }

  public List<List<TextPosition>> getPages() {
    return pages;
  }
}
