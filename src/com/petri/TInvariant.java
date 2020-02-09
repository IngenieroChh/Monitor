package com.petri;

import com.errors.IllegalPetriStateException;
import com.errors.OutsideWindowException;
import com.util.Parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TInvariant extends Invariant {

    private final static String LOGPATH = "res/log.txt";
    private final static String INVPATH = "res/t-invariantes.txt";

    public TInvariant(){
        parseInvariants(INVPATH);

    }

    public TInvariant(String file){
        parseInvariants(file);
    }


    /* Chequea que tras disparar las transiciones restantes, luego de remover las pertenecientes a una
         invariante de transición, de manera ordenada, el marcado (o estado) de la red,
         sea el mismo en el que se encontraba tras disparar la última transición de la simulación */
    @Override
    public boolean checkInvariants(Integer[] initialState) throws IllegalPetriStateException {

        Pattern[] patterns = new Pattern[invariantList.size()];
        ArrayList<String> replacers = new ArrayList<>();
        ArrayList<Matcher> matchers = new ArrayList<>();

        String data = "";

        try {
            data = new String(Files.readAllBytes(Paths.get(LOGPATH)));
        } catch (IOException e) {
            e.printStackTrace();
        }

        int index = 0;

        for(ArrayList<Integer> invariant : invariantList){

            String invariantPattern = "";
            String replace = "";

            for(int i=0; i<invariant.size()-1; i++) {
                invariantPattern += String.format("(T%d)([\\S\\s]+?)", invariant.get(i));
                replace += String.format("$%d", i*2+2);
            }

            invariantPattern += String.format("(T%d)", invariant.get(invariant.size()-1));

            patterns[index] = Pattern.compile(invariantPattern);
            matchers.add(patterns[index].matcher(data));
            replacers.add(replace);

            index++;
        }

        while (matchers.size() > 0){

            int position = data.length();
            int invariant = -1;

            for(int i=0; i<matchers.size(); i++){

                if(!matchers.get(i).find()){
                    matchers.remove(i);
                    replacers.remove(i);
                }
                else{

                    if(matchers.get(i).end() < position){
                        position = matchers.get(i).end();
                        invariant = i;
                    }

                }
            }

            if(invariant >= 0){
                data = matchers.get(invariant).replaceFirst(replacers.get(invariant));
            }

            for (Matcher matcher : matchers) {
                matcher.reset(data);
            }

        }

        for(ArrayList<Integer> invariant : invariantList){

            for(int j=0; j<invariant.size()-1; j++){

                String invariantPattern = "";
                String replace = "";

                for(int i=0; i<invariant.size()-2-j; i++) {
                    invariantPattern += String.format("(T%d)([\\S\\s]+?)", invariant.get(i));
                    replace += String.format("$%d", i*2+2);
                }

                invariantPattern += String.format("(T%d)", invariant.get(invariant.size()-2-j));

                Pattern pattern = Pattern.compile(invariantPattern);
                Matcher matcher = pattern.matcher(data);


                while (matcher.find()){
                    data = matcher.replaceFirst(replace);
                    matcher.reset(data);
                }



            }

        }

        Pattern pattern = Pattern.compile("T\\d+");
        Matcher matcher = pattern.matcher(data);

        if(matcher.find()){
            throw new IllegalPetriStateException("T-Invariants are not met\n"+data);
        }

        return true;
    }
}