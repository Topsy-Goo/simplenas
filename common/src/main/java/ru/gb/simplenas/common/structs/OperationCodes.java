package ru.gb.simplenas.common.structs;


public enum OperationCodes {
    NM_OPCODE_OK, //
    NM_OPCODE_LOGIN(){
        @Override public String getHeader () { return "Авторизация"; }
    },
    NM_OPCODE_LIST,   //< запрос с сервера содержимого каталога
    NM_OPCODE_CREATE(){
        @Override public String getHeader () { return "Создание папки"; }
    },
    NM_OPCODE_RENAME(){
        @Override public String getHeader () { return "Переименование"; }
    },
    NM_OPCODE_LOAD2LOCAL(){
        @Override public String getHeader () { return "Загрузка файла с сервера"; }
    },
    NM_OPCODE_LOAD2SERVER(){
        @Override public String getHeader () { return "Выгрузка файла на сервер"; }
    },
    NM_OPCODE_FILEINFO,   //< запрос информации о файле/папке
    NM_OPCODE_COUNTITEMS, //< количество элементов в папке
    NM_OPCODE_DELETE(){
        @Override public String getHeader () { return "Удаление"; }
    },
    NM_OPCODE_EXIT(){
        @Override public String getHeader () { return "Отключение"; }
    },
    NM_OPCODE_READY, //< принимающая сторона готова принимать файл
    NM_OPCODE_DATA, //< отдающая сторона передаёт часть файла / принимающая сторона приняла отправленную ей часть файла
    NM_OPCODE_ERROR(){
        @Override public String getHeader () { return "Ошибка?"; }
    };

    public String getHeader() { return ""; }
}

