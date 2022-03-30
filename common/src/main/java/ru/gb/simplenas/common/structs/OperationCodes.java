package ru.gb.simplenas.common.structs;


import java.util.concurrent.SynchronousQueue;

import static ru.gb.simplenas.common.CommonData.FAIR;
import static ru.gb.simplenas.common.Factory.sformat;

public enum OperationCodes
{
    NM_OPCODE_OK, //
    NM_OPCODE_LOGIN (new SynchronousQueue<>(FAIR)) {
        @Override public String getHeader () { return "Авторизация"; }
    },
    NM_OPCODE_LIST (new SynchronousQueue<>(FAIR)),   //< запрос с сервера содержимого каталога

    NM_OPCODE_CREATE (new SynchronousQueue<>(FAIR)) {
        @Override public String getHeader () { return "Создание папки"; }
    },
    NM_OPCODE_RENAME (new SynchronousQueue<>(FAIR)) {
        @Override public String getHeader () { return "Переименование"; }
    },
    NM_OPCODE_LOAD2LOCAL (new SynchronousQueue<>(FAIR)) {
        @Override public String getHeader () { return "Загрузка файла с сервера"; }
    },
    NM_OPCODE_LOAD2SERVER (new SynchronousQueue<>(FAIR)) {
        @Override public String getHeader () { return "Выгрузка файла на сервер"; }
    },
    NM_OPCODE_FILEINFO (new SynchronousQueue<>(FAIR)),   //< запрос информации о файле/папке
    NM_OPCODE_COUNTITEMS (new SynchronousQueue<>(FAIR)), //< количество элементов в папке
    NM_OPCODE_DELETE (new SynchronousQueue<>(FAIR)) {
        @Override public String getHeader () { return "Удаление"; }
    },
    NM_OPCODE_EXIT() {
        @Override public String getHeader () { return "Отключение"; }
    },
    NM_OPCODE_READY, //< принимающая сторона готова принимать файл
    NM_OPCODE_DATA, //< отдающая сторона передаёт часть файла / принимающая сторона приняла отправленную ей часть файла
    NM_OPCODE_ERROR() {
        @Override public String getHeader () { return "Ошибка?"; }
    };

    private SynchronousQueue <Object> synque;

    OperationCodes () {}
    OperationCodes (SynchronousQueue <Object> sque) {  synque = sque;  }

    /** Получить текст заголовка для окна сообщения. */
    public String getHeader() { return ""; }

    /** Получить SynchronousQueue для обмена данными между Клиентом и Манипулятором. */
    public SynchronousQueue<Object> getSynque () {
        if (synque == null)
            throw new UnsupportedOperationException (sformat ("Нельзя вызывать %s.getSynque()", name()));
        return synque;
    }
}

