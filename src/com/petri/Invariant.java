    package com.petri;

import com.util.Parser;

import java.util.ArrayList;

public abstract class Invariant {

    ArrayList<ArrayList<Integer>> invariantList;

    /* Se encarga de comprobar que se cumplan las invariantes */
    abstract public boolean checkInvariants(Integer[] initialState);

    /* Se encarga de parsear el documento que especifica las invariantes */
    void parseInvariants(String file){
        invariantList = new Parser(file, "\\d+", "(", ")").getParsedElements();
    }

}