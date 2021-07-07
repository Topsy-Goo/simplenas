package ru.gb.simplenas;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static ru.gb.simplenas.common.CommonData.*;
import static ru.gb.simplenas.server.SFactory.constructAbsoluteUserRoot;
import static ru.gb.simplenas.server.SFactory.isUserNameValid;
import static ru.gb.simplenas.server.services.impl.ServerFileManager.getSafeAbsolutePathBy;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

//класс для проверки возможности пользователя выйти за пределлы своего дискового пространства на сервере.
public class PathsTest
{
    @BeforeAll static void initAll()
    {
        if (CLOUD == null || !CLOUD.isAbsolute() || strFileSeparator == null || strFileSeparator.isEmpty())
        {
            Assertions.fail();
        }
    }
    //@AfterAll static void finishAll() {}
    //@BeforeEach void initEach() {}
    //@AfterEach void finishEach() {}

//------------------------- проверка корректности имени пользователя --------------------------------------------*/

    public static Stream<Arguments> generator4UserNameValidityTest()
    {
        List<Arguments> list = new ArrayList<>();
        list.add (Arguments.arguments (true, "5"));
        list.add (Arguments.arguments (true, "m"));
        list.add (Arguments.arguments (true, "user2021"));
        list.add (Arguments.arguments (true,  "123456789o123456789o123456789o12")); //< MAX_USERNAME_LENGTH = 32
        list.add (Arguments.arguments (false, "123456789o123456789o123456789o123"));
        list.add (Arguments.arguments (false, null));
        list.add (Arguments.arguments (false, STR_EMPTY));
        list.add (Arguments.arguments (false, "\\"));
        list.add (Arguments.arguments (false, "/"));
        list.add (Arguments.arguments (false, "|"));
        list.add (Arguments.arguments (false, ">"));
        list.add (Arguments.arguments (false, "<"));
        list.add (Arguments.arguments (false, ":"));
        list.add (Arguments.arguments (false, "^"));
        list.add (Arguments.arguments (false, "?"));
        list.add (Arguments.arguments (false, "*"));
        list.add (Arguments.arguments (false, "\n"));
        list.add (Arguments.arguments (false, "\r"));
        list.add (Arguments.arguments (false, "\t"));
        list.add (Arguments.arguments (false, " "));
        list.add (Arguments.arguments (false, "\""));
        list.add (Arguments.arguments (false, "."));  //< чтобы юзер не выбрал логин, совпадающий с названием какого-то служебного файла
        return list.stream();
    }

    @Order (1)
    @ParameterizedTest
    @MethodSource ("generator4UserNameValidityTest")
    public void testUserNameValidity (boolean ok, String name)
    {
        Assertions.assertEquals (ok, isUserNameValid (name));
    }

//------------------------- проверка генерирования абсолютного пути к корневой папке ДПП ------------------------*/

    public static Stream<Arguments> generator4ServerUserRootPathTest()
    {
        List<Arguments> list = new ArrayList<>();
        list.add (Arguments.arguments (true, "user1"));
        list.add (Arguments.arguments (true, STR_CLOUD));   //< сейчас юзер может выбрать себе такой логин
        list.add (Arguments.arguments (false, ".."+strFileSeparator+"user1"));
        list.add (Arguments.arguments (false, ".."+strFileSeparator+".."+strFileSeparator+"user1"));
        list.add (Arguments.arguments (false, ".."+strFileSeparator+".."+strFileSeparator));
        list.add (Arguments.arguments (false, ".."+strFileSeparator+".."));
        list.add (Arguments.arguments (false, ".."+strFileSeparator));
        list.add (Arguments.arguments (false, ".."));
        list.add (Arguments.arguments (false, "user1"+strFileSeparator));
        list.add (Arguments.arguments (false, "user1"+strFileSeparator+"tmp"));
        list.add (Arguments.arguments (false, STR_EMPTY));
        list.add (Arguments.arguments (false, null));
        return list.stream();
    }

    @Order (2)
    @ParameterizedTest
    @MethodSource ("generator4ServerUserRootPathTest")
    public void testServerUserRootPath (boolean ok, String name)
    {
        Path path = constructAbsoluteUserRoot (name);
        Assertions.assertEquals (ok,
            path != null
            && path.startsWith(CLOUD)
            && CLOUD.relativize(path).getNameCount() == 1
            && path.getFileName().toString().equals(name));
    }

//------------------------- проверка пути, передаваемого клиентом на сервер -------------------------------------*/

    public static Stream<Arguments> generator4ServerPathValidityTest()
    {
        List<Arguments> list = new ArrayList<>();
        list.add (Arguments.arguments (true,  "user1", CLOUD.resolve("user1")));
        list.add (Arguments.arguments (true,  "user1", CLOUD.resolve("user1").resolve("docs")));
        list.add (Arguments.arguments (true,  "user1", CLOUD.resolve("user1").resolve("docs").resolve("..")));
        list.add (Arguments.arguments (true,  "user1", CLOUD.resolve("user1").resolve(".")));
        list.add (Arguments.arguments (false, "user1", CLOUD.resolve(".")));
        list.add (Arguments.arguments (false, "user1", CLOUD.resolve("..")));
        list.add (Arguments.arguments (false, "user1", CLOUD.resolve("..").resolve("..")));
        list.add (Arguments.arguments (false, "user1", CLOUD.resolve("user1").resolve("..").resolve("..")));
        list.add (Arguments.arguments (false, "user1", CLOUD.resolve("user1").resolve("..").resolve("docs")));
        list.add (Arguments.arguments (false, "user1", CLOUD.resolve("")));
        list.add (Arguments.arguments (false, "user1", CLOUD.resolve("User2")));
        list.add (Arguments.arguments (false, "user1", CLOUD));
        list.add (Arguments.arguments (false, "user1", CLOUD.getRoot()));
        list.add (Arguments.arguments (false, "user1", CLOUD.getParent()));
        return list.stream();
    }

    @Order (3)
    @ParameterizedTest
    @MethodSource ("generator4ServerPathValidityTest")
    public void testServerPathValidity (boolean ok, String name, Path path)
    {
        Path userroot = constructAbsoluteUserRoot (name);
        if (userroot == null)
        {
            Assertions.fail();
        }
        else
        {
            Path result = getSafeAbsolutePathBy (path, name);
            Assertions.assertEquals (ok,
                result != null
                && result.isAbsolute()
                && result.startsWith(userroot));
        }
    }
}
//---------------------------------------------------------------------------------------------------------------*/
