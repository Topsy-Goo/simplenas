package ru.gb.simplenas.client.services.impl;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.text.TextAlignment;
import javafx.util.Callback;

import java.text.DecimalFormat;
import java.text.Format;

import static ru.gb.simplenas.common.CommonData.STR_EMPTY;


public class FormattedTableCellFactory<P, T> implements Callback<TableColumn<P, T>, TableCell<P, T>> {
    private TextAlignment alignment;
    private Format format = new DecimalFormat("###,###,###,###,###");


    @Override @SuppressWarnings ("unchecked") public TableCell<P, T> call (TableColumn<P, T> param) {
        TableCell<P, T> cell = new TableCell<P, T>() {
            @Override public void updateItem (Object item, boolean empty) //< как оказалось, item это значение ячейки, «обёрнутое», если оно задавалось примитивом.
            {
                if (item == getItem()) { return; }

                super.updateItem((T) item, empty);

                if (item == null) {
                    super.setText(null);
                    super.setGraphic(null);
                }
                else if (format != null) {
                    if (((Long) item) >= 0) {
                        super.setText(format.format(item));
                    }
                    else { super.setText(STR_EMPTY); }
                }
                else if (item instanceof Node) {
                    super.setText(null);
                    super.setGraphic((Node) item);
                }
                else {
                    super.setText(item.toString());
                    super.setGraphic(null);
                }
            }
        };

        cell.setTextAlignment(alignment);

        switch (alignment) {
            case CENTER:
                cell.setAlignment(Pos.CENTER);
                break;
            case RIGHT:
                cell.setAlignment(Pos.CENTER_RIGHT);
                break;
            default:
                cell.setAlignment(Pos.CENTER_LEFT);
                break;
        }
        return cell;
    }

    public TextAlignment getAlignment () { return alignment; }

    public Format getFormat () { return format; }

    public void setAlignment (TextAlignment alignment) { this.alignment = alignment; }

    public void setFormat (Format format) { this.format = format; }

}
