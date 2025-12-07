import java.util.Random;

public class Task1 {

    static class MatrixThread extends Thread {
        private double[][] A;
        private double[][] B;
        private double[][] C;
        private int startRow;   // начальная строка для обработки потоком
        private int endRow;     // конечная строка (не включается)
        private int size;       // размер матриц

        public MatrixThread(double[][] A, double[][] B, double[][] C,
                            int startRow, int endRow, int size) {
            this.A = A;
            this.B = B;
            this.C = C;
            this.startRow = startRow;
            this.endRow = endRow;
            this.size = size;
        }

        @Override
        public void run() {
            for (int i = startRow; i < endRow; i++) {
                for (int j = 0; j < size; j++) {
                    double sum = 0;
                    for (int k = 0; k < size; k++) {
                        sum += A[i][k] * B[k][j];
                    }
                    C[i][j] = sum;
                }
            }
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Ошибка: неверное количество аргументов");
            System.out.println("Использование: java Task1 <размер_матрицы> <макс_потоков>");
            return;
        }

        // парсинг аргументов
        int size = Integer.parseInt(args[0]);        // размер матрицы
        int maxThreads = Integer.parseInt(args[1]);  // число потоков для тестирования

        System.out.println("Размер матрицы: " + size + "x" + size);
        System.out.println("Количество замеров для каждого числа потоков: 3");

        // Создание и заполнение матриц
        double[][] A = new double[size][size];
        double[][] B = new double[size][size];
        double[][] C_seq = new double[size][size];

        Random rand = new Random();
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                A[i][j] = rand.nextDouble() * 100;  // от 0 до 100
                B[i][j] = rand.nextDouble() * 100;
            }
        }

        System.out.println("\nПоследовательное умножение:");

        // Измеряем последовательное выполнение 3 раза
        long seqTotalTime = 0;
        for (int measurement = 1; measurement <= 3; measurement++) {
            long start = System.nanoTime();  // время начала

            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    double sum = 0;
                    for (int k = 0; k < size; k++) {
                        sum += A[i][k] * B[k][j];
                    }
                    C_seq[i][j] = sum;
                }
            }

            long seqTime = System.nanoTime() - start;  // время выполнения
            seqTotalTime += seqTime;
            System.out.printf("Замер %d: %.3f мс\n", measurement, seqTime / 1_000_000.0);
        }

        double avgSeqTime = seqTotalTime / 3.0;
        System.out.printf("Среднее время: %.3f мс\n\n", avgSeqTime / 1_000_000.0);

        System.out.println("Параллельное умножение:");
        System.out.println("Потоки | Время(мс) | Ускорение | Замер 1 | Замер 2 | Замер 3");
        System.out.println("-------|-----------|-----------|---------|---------|---------");

        // разное количество потоков (от 1 до maxThreads)
        for (int numThreads = 1; numThreads <= maxThreads; numThreads++) {
            long[] measurements = new long[3];

            // Выполняем 3 замера для текущего числа потоков
            for (int measurement = 0; measurement < 3; measurement++) {
                double[][] result = new double[size][size];

                // время начала
                long start = System.nanoTime();

                // массив потоков
                MatrixThread[] threads = new MatrixThread[numThreads];

                // распределение работы между потоками
                int rowsPerThread = size / numThreads;    // количество строк на поток
                int extraRows = size % numThreads;        // остаток строк для распределения

                int currentRow = 0;  // текущая строка

                // создаем каждый поток
                for (int t = 0; t < numThreads; t++) {
                    int startRow = currentRow;
                    int endRow = startRow + rowsPerThread + (t < extraRows ? 1 : 0);

                    threads[t] = new MatrixThread(A, B, result, startRow, endRow, size);

                    currentRow = endRow;
                }

                // запуск
                for (int t = 0; t < numThreads; t++) {
                    threads[t].start();  // потоки начинают выполнение параллельно
                }

                // ожидание завершения все потоков
                try {
                    for (int t = 0; t < numThreads; t++) {
                        threads[t].join();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                long parTime = System.nanoTime() - start;  // время параллельного выполнения
                measurements[measurement] = parTime;
            }

            // Вычисляем среднее время для текущего числа потоков
            long totalTime = 0;
            for (int i = 0; i < 3; i++) {
                totalTime += measurements[i];
            }
            double avgParTime = totalTime / 3.0;

            // Ускорение = среднее время последовательного выполнения / среднее время параллельного выполнения
            double speedup = avgSeqTime / avgParTime;

            System.out.printf("%6d | %9.3f | %9.2f | %7.3f | %7.3f | %7.3f\n",
                    numThreads,                     // кол-во потоков
                    avgParTime / 1_000_000.0,       // среднее время в миллисекундах
                    speedup,                        // коэффициент ускорения
                    measurements[0] / 1_000_000.0,  // замер 1
                    measurements[1] / 1_000_000.0,  // замер 2
                    measurements[2] / 1_000_000.0   // замер 3
            );
        }
    }
}