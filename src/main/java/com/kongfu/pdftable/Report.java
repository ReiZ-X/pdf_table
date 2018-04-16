package com.kongfu.pdftable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * @author chengyibin
 */
public abstract class Report {

  public Report(String pdfPath) {
    this.pdfPath = pdfPath;
  }

  private String pdfPath;
  private final static int defaultOffset = 2;
  private final static int defaultColOffset = 0;
  private final static int defaultRowPadding = 12;
  private final static int defaultSpacing = 45;

  /**
   * @return 返回表格开始行的特征信息，比如爱康国宾体检报告特征是行文字包括:"检查者：" 或 "操作者：" 或 "审核者："
   */
  public abstract boolean isStartTable(String line);

  /**
   * @return 返回表格结束行的特征信息，比如爱康国宾体检报告是行文字包含："小结" 或 "初步意见"
   */
  public abstract boolean isEndTable(String line);

  /**
   * 判断这行是页眉
   *
   * @param line 行内容
   * @return 是否页眉
   */
  public abstract boolean isPageHeader(String line);

  /**
   * 判断这行是否页脚
   *
   * @param line 行内容
   * @return 是否页脚
   */
  public abstract boolean isPageFooter(String line);

  /**
   * 判断这行是否需要忽略的行
   *
   * @param line 行内容
   * @return 是否忽略
   */
  public abstract boolean isIgnoreLine(String line);

  /**
   * 判断切出来的行里面有哪些行应该是在同一个单元格里面的，如果两行的间距小于rowPadding，那么将他们合并
   * 根据实际情况进行重载
   *
   * @return 间距
   */
  public int getRowPadding() {
    return defaultRowPadding;
  }

  /**
   * @return 进行行切割的时候，判断切割线是否与文字有交叉，在此基础上再加上偏移量进行判定
   */
  public int getRowOffset() {
    return defaultOffset;
  }

  public int getColOffset() {
    return defaultColOffset;
  }

  /***
   * 如果是表头或者表尾 默认切割为两列，判断切割点的方法是间隔spacing没有交叉，那么以此作为切割线
   * @return 间隔
   */
  public int getSpacing() {
    return defaultSpacing;
  }

  /**
   * 从哪一行开始解析表格，eg.爱康的“健康体检结果”代表可以解析了，
   * ！！！如果没有明显特征，请返回null
   *
   * @return 开始行特征
   */
  public abstract String getBegin2StartRow();

  /**
   * @return 返回表格list 单个表格的第一行为表头，最后一行为表尾
   * @throws IOException 异常
   */
  public List<List<List<String>>> parse() throws IOException {
    MyTextStripper ts = new MyTextStripper();
    List<List<TextPosition>> pages = ts.parsePdfTable(pdfPath);
    List<List<List<TextPosition>>> tables = new ArrayList<>();
    List<List<TextPosition>> table = new ArrayList<>();
    boolean afterBeginRow = false;
    boolean canStart = false;
    int tag = 0;
    for (List<TextPosition> charList : pages) {
      List<Integer> ys = new ArrayList<>();
      boolean begin = false;
      ys.add(-1);
      int max = max(charList);
      for (int i = -1; i < max; i++) {
        boolean hasCross = hasCross(i, charList);
        if (hasCross && !begin) {
          ys.add(i - 1);
          begin = true;
        }
        if (!hasCross && begin) {
//        ys.add(i);
          begin = false;
        }
      }
      ys.add(max + 10);
      List<List<TextPosition>> lines = toLineWithY(ys, charList);
      //过滤
      for (Iterator<List<TextPosition>> ite = lines.iterator(); ite.hasNext(); ) {
        List<TextPosition> line = ite.next();
        StringBuilder s = new StringBuilder();
        for (TextPosition pos : line) {
          s.append(pos.getUnicode());
        }
        if (isPageHeader(s.toString()) || isPageFooter(s.toString()) || isIgnoreLine(s.toString())) {
          ite.remove();
        }
      }
      //组装成新table
      for (List<TextPosition> line : lines) {
        StringBuilder s = new StringBuilder();
        for (TextPosition pos : line) {
          s.append(pos.getUnicode());
        }
        if (StringUtils.isEmpty(getBegin2StartRow()) ||
            s.toString().contains(getBegin2StartRow())) {
          afterBeginRow = true;
        }
        if (afterBeginRow) {
          if (isStartTable(s.toString())) {
            if (tag++ > 0) {
              tables.add(table);
              table = new ArrayList<>();
            }
            canStart = true;
          }
          if (canStart) {
            table.add(line);
          }
        }
      }
    }
    //合并表尾
    for (int j = 0; j < tables.size(); j++) {
      List<List<TextPosition>> t = tables.get(j);
      t = mergeTailRows(t);
      tables.set(j, t);
    }

    List<List<List<String>>> allTable = new ArrayList<>();
    for (List<List<TextPosition>> t : tables) {
      List<TextPosition> headerList = t.get(0);
      List<String> headerRow = divide2Part(headerList, false);
      t.remove(0);
      List<TextPosition> footerList = t.get(t.size() - 1);
      List<String> footerRow = divide2Part(footerList, true);
      if (!CollectionUtils.isEmpty(footerRow)) {
        t.remove(t.size() - 1);
      }
      List<List<String>> rows = splitTableCol(t);
      //去掉全为空格的行
      outer:
      for (Iterator<List<String>> ite = rows.iterator(); ite.hasNext(); ) {
        List<String> row = ite.next();
        for (String cell : row) {
          if (!StringUtils.isEmpty(cell.trim())) {
            continue outer;
          }
        }
        ite.remove();
      }
      //判断是否是表尾的行在这里，判断依据是第一个单元格为空白 todo 可去掉 因为上面已经mergeTailRows
      List<String> row = rows.get(rows.size() - 1);
      if (StringUtils.isEmpty(row.get(0).trim())) {
        String s = footerRow.get(1) + row.get(1);
        footerRow.set(1, s);
        rows.remove(rows.size() - 1);
      }
      rows.add(0, headerRow);
      if (!CollectionUtils.isEmpty(footerRow)) {
        rows.add(footerRow);
      }
      allTable.add(rows);
    }
    return allTable;
  }

  //根据对称性
  public List<List<TextPosition>> mergeTailRows(List<List<TextPosition>> t) {
    List<String> l = new ArrayList<>();
    for (Iterator<List<TextPosition>> ite = t.iterator(); ite.hasNext(); ) {
      List<TextPosition> tl = ite.next();
      StringBuilder s = new StringBuilder();
      for (TextPosition tp : tl) {
        s.append(tp.getUnicode());
      }
      if (StringUtils.isEmpty(s.toString())) {
        ite.remove();
      } else {
        l.add(s.toString());
      }
    }
    l.removeIf(line -> StringUtils.isEmpty(line.trim()));
    int count = 0;
    int idx = -1;
    boolean begin = false;
    for (int i = 0; i < l.size(); i++) {
      String s = l.get(i);
      if (isEndTable(s)) {
        begin = true;
        idx = i;
        continue;
      }
      if (begin) {
        count++;
      }
    }
    List<TextPosition> ll = new ArrayList<>();
    if (count > 0) {
      for (int i = 1; i <= count; i++) {
        ll.addAll(t.get(idx + i));
        ll.addAll(t.get(idx - i));
      }
      for (int i = 1; i <= count; i++) {
        t.remove(t.size() - 1);
      }
      for (int i = 1; i <= count; i++) {
        t.remove(t.size() - 2);
      }
    }
    ll.addAll(t.get(t.size() - 1));
    t.set(t.size() - 1, ll);
    return t;
  }

  public List<List<String>> splitTableCol(List<List<TextPosition>> table) {
    List<TextPosition> all = new ArrayList<>();
    for (List<TextPosition> row : table) {
      all.addAll(row);
    }
    //cut
    List<Float> xs = new ArrayList<>();
    boolean begin = false;
    float max = maxX(all);
    for (float i = -1; i < max; i += 0.5) {
      boolean hasCross = hasCrossY(i, all);
      if (hasCross && !begin) {
        xs.add(i - 1);
        begin = true;
      }
      if (!hasCross && begin) {
//        ys.add(i);
        begin = false;
      }
    }
    xs.add(max + 10);
    return toLineWithX(xs, table);
  }

  public List<List<String>> toLineWithX(List<Float> xs, List<List<TextPosition>> rows) {
    List<List<String>> allRow = new ArrayList<>();
    List<List<List<TextPosition>>> r = new ArrayList<>();
    for (List<TextPosition> row : rows) {
      float preX = xs.get(0);
      List<List<TextPosition>> l = new ArrayList<>();
      for (int i = 1; i < xs.size(); i++) {
        List<TextPosition> ll = new ArrayList<>();
        float x = xs.get(i);
        for (TextPosition pos : row) {
          if (pos.getX() > preX && pos.getX() + pos.getWidth() < x) {
            ll.add(pos);
          }
        }
        l.add(ll);
        preX = x;
      }
      r.add(l);
    }

    r = mergeRows(r);
    for (List<List<TextPosition>> list : r) {
      List<String> sList = new ArrayList<>();
      for (List<TextPosition> tList : list) {
        StringBuilder s = new StringBuilder();
        for (TextPosition tp : tList) {
          s.append(tp.getUnicode());
        }
        sList.add(s.toString());
      }
      allRow.add(sList);
    }
    return allRow;
  }

  private List<List<List<TextPosition>>> mergeRows(List<List<List<TextPosition>>> rows) {
    List<List<TextPosition>> preRow = rows.get(0);
    int idx = 0;
    for (Iterator<List<List<TextPosition>>> ite = rows.iterator(); ite.hasNext(); ) {
      List<List<TextPosition>> row = ite.next();
      if (idx++ == 0) {
        continue;
      }
      if (canMerge(preRow, row)) {
        for (int i = 0; i < preRow.size(); i++) {
          preRow.get(i).addAll(row.get(i));
        }
        ite.remove();
      } else {
        preRow = row;
      }
    }
    return rows;
  }

  private boolean canMerge(List<List<TextPosition>> preRow, List<List<TextPosition>> row) {
    if (CollectionUtils.isEmpty(preRow) || CollectionUtils.isEmpty(row)) {
      return false;
    }
    for (int i = 0; i < preRow.size(); i++) {
      List<TextPosition> textPositions1 = preRow.get(i);
      float max = -1;
      for (TextPosition tp : textPositions1) {
        if (tp.getY() > max) {
          max = tp.getY();
        }
      }
      List<TextPosition> textPositions2 = row.get(i);
      float min = 10000;
      for (TextPosition tp : textPositions2) {
        if (tp.getY() < min) {
          min = tp.getY();
        }
      }
      if (Math.abs(min - max) <= getRowPadding()) {
        return true;
      }
    }
    return false;
  }

  public List<List<TextPosition>> toLineWithY(List<Integer> ys, List<TextPosition> list) {
    if (CollectionUtils.isEmpty(ys) || CollectionUtils.isEmpty(list)) {
      return new ArrayList<>();
    }
    List<List<TextPosition>> lines = new ArrayList<>();
    int preY = ys.get(0);
    for (int i = 1; i < ys.size(); i++) {
      List<TextPosition> sameLine = new ArrayList<>();
      for (TextPosition pos : list) {
        if (pos.getY() > preY && pos.getY() + pos.getHeight() < ys.get(i)) {
          sameLine.add(pos);
        }
      }
      sameLine.sort((o1, o2) -> {
        if (o1.getY() < o2.getY()) {
          return -1;
        } else if (o1.getY() == o2.getY()) {
          return Float.compare(o1.getX(), o2.getX());
        } else {
          return 1;
        }
      });
      lines.add(sameLine);
      preY = ys.get(i);
    }
    return lines;
  }

  private List<String> divide2Part(List<TextPosition> list, boolean isFooter) {
    int min = 10000;
    int max = -1;
    for (TextPosition tp : list) {
      if (tp.getX() < min) {
        min = (int) tp.getX();
      }
      if (tp.getX() > max) {
        max = (int) tp.getX();
      }
    }
    int critical = -1;
    int preCross = -1;
    for (int i = min; i < max; i++) {
      if (hasCrossY(i, list)) {
        preCross = i;
      } else if (i - preCross >= getSpacing()) {
        critical = i;
      }
    }
    if (critical == -1) {
      return new ArrayList<>();
    }

    List<TextPosition> leftList = new ArrayList<>();
    List<TextPosition> rightList = new ArrayList<>();
    for (TextPosition tp : list) {
      if (tp.getX() < critical) {
        leftList.add(tp);
      } else {
        rightList.add(tp);
      }
    }
    leftList.sort((o1, o2) -> Float.compare(o1.getX(), o2.getX()));
    rightList.sort((o1, o2) -> {
      if (o1.getY() < o2.getY()) {
        return -1;
      } else if (o1.getY() == o2.getY()) {
        return Float.compare(o1.getX(), o2.getX());
      } else {
        return 1;
      }
    });
    List<String> result = new ArrayList<>();
    StringBuilder s = new StringBuilder();
    for (TextPosition tp : leftList) {
      s.append(tp.getUnicode());
    }
    if (!isEndTable(s.toString()) && isFooter) {
      return new ArrayList<>();
    }
    result.add(s.toString());
    s = new StringBuilder();
    for (TextPosition tp : rightList) {
      s.append(tp.getUnicode());
    }
    result.add(s.toString());
    return result;
  }


  private boolean hasCross(int c, List<TextPosition> list) {
    for (TextPosition pos : list) {
      float begin = pos.getY();
      float end = pos.getY() + pos.getHeight() + getRowOffset();
      if (c >= begin && c <= end) {
        return true;
      }
    }
    return false;
  }


  private boolean hasCrossY(float c, List<TextPosition> list) {
    for (TextPosition pos : list) {
      float begin = pos.getX();
      float end = pos.getX() + pos.getWidth() + getColOffset();
      if (c >= begin && c <= end) {
        return true;
      }
    }
    return false;
  }

  private int max(List<TextPosition> charList) {
    int max = -1;
    for (TextPosition tp : charList) {
      if (tp.getY() + tp.getHeight() > max) {
        max = (int) (tp.getY() + tp.getHeight());
      }
    }
    return max + 1;
  }

  private float maxX(List<TextPosition> list) {
    float max = -1;
    for (TextPosition tp : list) {
      if (tp.getX() > max) {
        max = tp.getX();
      }
    }
    return max + 1;
  }

  public static void printRealTables(List<List<List<String>>> tables) {
    for (List<List<String>> table : tables) {
      System.out.println("**********************************************");
      for (List<String> row : table) {
        for (String cell : row) {
          System.out.print("<" + cell + ">");
        }
        System.out.println();
      }
      System.out.println("**********************************************");
    }
  }
}
