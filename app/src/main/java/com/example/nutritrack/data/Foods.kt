package com.example.nutritrack.data

/** Valores aproximados por 100 g (o 100 ml). Edítalos o amplíalos a tu gusto. */
data class Food(val name: String, val kcal: Double, val p: Double, val c: Double, val f: Double)

val FOODS = listOf(
    Food("Pechuga de pollo cocida", 165.0, 31.0, 0.0, 3.6),
    Food("Pechuga de pavo cocida", 135.0, 30.0, 0.0, 1.0),
    Food("Carne de res magra cocida", 250.0, 26.0, 0.0, 15.0),
    Food("Salmón cocido", 206.0, 22.0, 0.0, 12.0),
    Food("Atún en agua", 116.0, 26.0, 0.0, 1.0),
    Food("Huevo entero", 143.0, 12.6, 0.7, 9.5),
    Food("Clara de huevo", 52.0, 11.0, 0.7, 0.2),
    Food("Leche entera (ml)", 61.0, 3.2, 4.8, 3.3),
    Food("Yogur natural", 61.0, 3.5, 4.7, 3.3),
    Food("Yogur griego natural", 97.0, 9.0, 4.0, 5.0),
    Food("Queso fresco", 264.0, 18.0, 3.0, 20.0),
    Food("Proteína whey (polvo)", 400.0, 80.0, 8.0, 6.0),
    Food("Arroz blanco cocido", 130.0, 2.7, 28.0, 0.3),
    Food("Pasta cocida", 158.0, 5.8, 31.0, 0.9),
    Food("Avena en hojuelas", 389.0, 16.9, 66.0, 6.9),
    Food("Pan blanco", 265.0, 9.0, 49.0, 3.2),
    Food("Tortilla de maíz", 218.0, 5.7, 45.0, 2.9),
    Food("Papa cocida", 87.0, 1.9, 20.0, 0.1),
    Food("Camote cocido", 90.0, 2.0, 21.0, 0.2),
    Food("Frijoles cocidos", 127.0, 8.7, 23.0, 0.5),
    Food("Lentejas cocidas", 116.0, 9.0, 20.0, 0.4),
    Food("Garbanzos cocidos", 164.0, 8.9, 27.0, 2.6),
    Food("Banana / plátano", 89.0, 1.1, 23.0, 0.3),
    Food("Manzana", 52.0, 0.3, 14.0, 0.2),
    Food("Brócoli", 34.0, 2.8, 7.0, 0.4),
    Food("Tomate", 18.0, 0.9, 3.9, 0.2),
    Food("Aguacate", 160.0, 2.0, 9.0, 15.0),
    Food("Almendras", 579.0, 21.0, 22.0, 50.0),
    Food("Mantequilla de maní", 588.0, 25.0, 20.0, 50.0),
    Food("Aceite de oliva", 884.0, 0.0, 0.0, 100.0),
)
