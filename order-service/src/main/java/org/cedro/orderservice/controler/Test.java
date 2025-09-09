package org.cedro.orderservice.controler;


import java.util.ArrayList;
import java.util.List;

public class Test {


    public static void main(String[] args) {

        List<Integer> numeros = new ArrayList<>();

        numeros = List.of(1, 2, 3, 4, 5);

        Integer[] numbers = {1, 2, 3};


        for (int i = numbers.length - 1; i >= 0; i--) {
            System.out.println("aqui um numero => " + i);
        }

        for (int i = 0; i < numbers.length; i++) {
            System.out.println("aqui um numero => " + i);
        }
    }



}
