package ru.gb.simplenas.common.structs;


public enum OperationCodes {
    NM_OPCODE_OK, //
    NM_OPCODE_LOGIN, //
    NM_OPCODE_LIST,   //< запрос с сервера содержимого каталога
    NM_OPCODE_CREATE, //< запрос на создание папки
    NM_OPCODE_RENAME, //< переименование файла или папки
    NM_OPCODE_LOAD2LOCAL, //< передача файла от сервера к клиенту
    NM_OPCODE_LOAD2SERVER,//< передача файла от клиента на сервер
    NM_OPCODE_FILEINFO,   //< запрос информации о файле/папке
    NM_OPCODE_COUNTITEMS, //< количество элементов в папке
    NM_OPCODE_DELETE,     //< удаление папки или файла
    NM_OPCODE_EXIT, //
    NM_OPCODE_READY, //< принимающая сторона готова принимать файл
    NM_OPCODE_DATA, //< отдающая сторона передаёт часть файла / принимающая сторона приняла отправленную ей часть файла
    NM_OPCODE_ERROR;
}

