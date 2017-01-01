package com.manu.projeto.novostockhawk.data;

import net.simonvt.schematic.annotation.Database;
import net.simonvt.schematic.annotation.Table;

/**
 * Created by emanu on 01/01/2017.
 */
//cria o banco de dados na versão indicada
@Database(version = QuoteDataBase.VERSION)
public class QuoteDataBase {
    private QuoteDataBase(){}

    public static final int VERSION = 1;

    //monta a tabela do banco com os campos declarados em QuotesColumns
    @Table(QuoteColumns.class) public static final String QUOTES = "quotes";
}
