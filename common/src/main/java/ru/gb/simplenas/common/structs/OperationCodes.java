package ru.gb.simplenas.common.structs;


public enum OperationCodes {
    OK,
    LOGIN,
    LIST,   //< запрос с сервера содержимого каталога
    CREATE, //< запрос на создание папки
    RENAME, //< переименование файла или папки
    LOAD2LOCAL, //< передача файла от сервера к клиенту
    LOAD2SERVER,//< передача файла от клиента на сервер
    FILEINFO,   //< запрос информации о файле/папке
    COUNTITEMS, //< количество элементов в папке
    DELETE,     //< удаление папки или файла
    EXIT,
    ERROR;
}

