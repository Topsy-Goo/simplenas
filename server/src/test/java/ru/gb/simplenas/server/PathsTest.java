package ru.gb.simplenas.server;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ru.gb.simplenas.server.services.ServerFileManager;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static ru.gb.simplenas.common.CommonData.STR_EMPTY;
import static ru.gb.simplenas.common.CommonData.strFileSeparator;
import static ru.gb.simplenas.common.Factory.lnprint;
import static ru.gb.simplenas.common.Factory.printf;
import static ru.gb.simplenas.server.SFactory.getServerFileManager;
import static ru.gb.simplenas.server.SFactory.isNameValid;

//класс для проверки возможности пользователя выйти за пределлы своего дискового пространства на сервере.
public class PathsTest
{
    private static Path cloud;
    private static final String strCloudName = "cloudtest";
    private static ServerFileManager sfm;


    @BeforeAll static void initAll()
    {
        //(Почему-то, глупая софтина считает умолчальным путём папку server, а не папку проекта.
        // Пока это не мешает тестированию.)
        sfm = getServerFileManager (/*".." + strFileSeparator +*/ strCloudName);

        cloud = sfm.getCloud();
        printf("\nT.Папка облака: <%s>", cloud);

        if (strFileSeparator == null || strFileSeparator.isEmpty())
        {
            Assertions.fail();
        }
    }

//------------------------- проверка корректности имени пользователя --------------------------------------------*/

    public static Stream<Arguments> generator4UserNameValidityTest()
    {
        List<Arguments> list = new ArrayList<>();
        list.add (Arguments.arguments (true, "5"));
        list.add (Arguments.arguments (true, "m"));
        list.add (Arguments.arguments (true, "user2021"));
        list.add (Arguments.arguments (false, " user2021"));
        list.add (Arguments.arguments (false, "user2021 "));
        list.add (Arguments.arguments (false, "user2021\t"));
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
        boolean validated = isNameValid(name);
        Assertions.assertEquals(ok, validated);
    }

//------------------------- проверка генерирования абсолютного пути к корневой папке ДПП ------------------------*/

    public static Stream<Arguments> generator4ServerUserRootPathTest()
    {
        List<Arguments> list = new ArrayList<>();
        list.add (Arguments.arguments (true, "user1"));
        list.add (Arguments.arguments (true, strCloudName));   //< сейчас юзер может выбрать себе такой логин
        list.add (Arguments.arguments (false, ".." +strFileSeparator+"user1"));
        list.add (Arguments.arguments (false, ".." +strFileSeparator+ ".." +strFileSeparator+"user1"));
        list.add (Arguments.arguments (false, ".." +strFileSeparator+ ".." +strFileSeparator));
        list.add (Arguments.arguments (false, ".." +strFileSeparator+ ".."));
        list.add (Arguments.arguments (false, ".." +strFileSeparator));
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
        Path path = sfm.constructAbsoluteUserRoot (name);
        Assertions.assertEquals (ok,
            path != null
            && path.startsWith (cloud)
            && cloud.relativize(path).getNameCount() == 1
            && path.getFileName().toString().equals(name)
            );
    }

//------------------------- проверка пути, передаваемого клиентом на сервер -------------------------------------*/

    public static Stream<Arguments> generator4ServerPathValidityTest()
    {
        List<Arguments> list = new ArrayList<>();
        list.add(Arguments.arguments (true,  "user1", cloud.resolve("user1")));
        list.add(Arguments.arguments (true,  "user1", cloud.resolve("user1").resolve("docs")));
        list.add(Arguments.arguments (true,  "user1", cloud.resolve("user1").resolve("docs").resolve("test")));
        list.add(Arguments.arguments (true,  "user1", cloud.resolve("user1").resolve(".")));
        list.add(Arguments.arguments (false, "user1", cloud.resolve(".")));
        list.add(Arguments.arguments (false, "user1", cloud.resolve("..")));
        list.add(Arguments.arguments (false, "user1", cloud.resolve("..").resolve("..")));
        list.add(Arguments.arguments (false, "user1", cloud.resolve("user1").resolve("..").resolve("..")));
        list.add(Arguments.arguments (false, "user1", cloud.resolve("user1").resolve("..").resolve("docs")));
        list.add(Arguments.arguments (false, "user1", cloud.resolve("")));
        list.add(Arguments.arguments (false, "user1", cloud.resolve("User2")));
        list.add(Arguments.arguments (false, "user1", cloud));
        list.add(Arguments.arguments (false, "user1", cloud.getRoot()));
        list.add(Arguments.arguments (false, "user1", cloud.getParent()));
        return list.stream();
    }

    @Order (3)
    @ParameterizedTest
    @MethodSource ("generator4ServerPathValidityTest")
    public void testServerPathValidity (boolean ok, String name, Path path)
    {
        Path userroot = sfm.constructAbsoluteUserRoot (name);
        if (userroot == null)
        {
            Assertions.fail();
        }
        else
        {
            Path result = sfm.getSafeAbsolutePathBy (path, name);
            Assertions.assertEquals (ok,
                result != null
                && result.isAbsolute()
                && result.startsWith(userroot));
        }
    }
}
//---------------------------------------------------------------------------------------------------------------*/
