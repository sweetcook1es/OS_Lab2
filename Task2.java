import java.util.Random;
import java.io.*;
import java.util.ArrayList;

public class Task2 {

    private static final String MATRIX_A_FILE = "A.bin";
    private static final String MATRIX_B_FILE = "B.bin";

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Ошибка: неверное количество аргументов");
            System.out.println("Использование: java Task2 <размер_матрицы> <макс_процессов>");
            return;
        }

        if (args[0].equals("child")) {
            runChildProcess(args);
        } else {
            runParentProcess(args);
        }
    }

    private static void runParentProcess(String[] args) throws Exception {
        int size = Integer.parseInt(args[0]);          // размер матрицы
        int maxProcesses = Integer.parseInt(args[1]);  // число процессов

        System.out.println("Размер матрицы: " + size + "x" + size);
        System.out.println("Количество замеров для каждого числа процессов: 3");

        // Создание и заполнение матриц
        double[][] A = new double[size][size];
        double[][] B = new double[size][size];
        Random r = new Random();

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                A[i][j] = r.nextDouble() * 100;  // от 0 до 100
                B[i][j] = r.nextDouble() * 100;
            }
        }

        // Сохранение матриц в файлы
        saveMatrixBinary(A, MATRIX_A_FILE);
        saveMatrixBinary(B, MATRIX_B_FILE);

        System.out.println("\nПоследовательное умножение:");
        long seqTotalTime = 0;

        // Выполняем 3 замера для последовательного умножения
        for (int measurement = 1; measurement <= 3; measurement++) {
            long seqStart = System.nanoTime();
            double[][] C_seq = multiplySequential(A, B);
            long seqTime = System.nanoTime() - seqStart;
            seqTotalTime += seqTime;
            System.out.printf("Замер %d: %.3f мс\n", measurement, seqTime / 1_000_000.0);
        }

        double avgSeqTime = seqTotalTime / 3.0;
        System.out.printf("Среднее время: %.3f мс\n\n", avgSeqTime / 1_000_000.0);

        System.out.println("Параллельное умножение:");
        System.out.println("Процессы | Время(мс) | Ускорение | Замер 1 | Замер 2 | Замер 3");
        System.out.println("---------|-----------|-----------|---------|---------|---------");

        // разное количество процессов
        for (int numProcesses = 1; numProcesses <= maxProcesses; numProcesses++) {
            long[] measurements = new long[3];

            // Выполняем 3 замера для текущего числа процессов
            for (int measurement = 0; measurement < 3; measurement++) {
                long parStart = System.nanoTime();

                ArrayList<Process> children = new ArrayList<>();
                ArrayList<String> resultFiles = new ArrayList<>();

                // распределение работы
                int rowsPerProc = size / numProcesses;  // количество строк на процесс
                int extraRows = size % numProcesses;    // остаток строк

                int currentRow = 0;                     // текущая строка

                // запуск процессов параллельно
                for (int pid = 0; pid < numProcesses; pid++) {
                    int startRow = currentRow;
                    int endRow = startRow + rowsPerProc + (pid < extraRows ? 1 : 0);
                    currentRow = endRow;

                    String resultFile = "result_" + pid + "_" + numProcesses + "_" + measurement + ".bin";
                    resultFiles.add(resultFile);

                    // создание дочернего процесса
                    ProcessBuilder pb = new ProcessBuilder(
                            "java", "Task2", "child",
                            String.valueOf(pid),
                            String.valueOf(startRow),
                            String.valueOf(endRow),
                            String.valueOf(size),
                            MATRIX_A_FILE,
                            MATRIX_B_FILE,
                            resultFile
                    );

                    pb.redirectErrorStream(false);
                    pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);

                    children.add(pb.start());
                }

                // ожидание завершения всех процессов
                for (Process child : children) {
                    child.waitFor();
                }

                long parTime = System.nanoTime() - parStart;
                measurements[measurement] = parTime;

                // чтение всех файлов результатов
                double[][] C_par = new double[size][size];
                for (String file : resultFiles) {
                    loadResultPart(file, C_par);
                }

                // удаляем файлы с результатами
                for (String file : resultFiles) {
                    new File(file).delete();
                }
            }

            // Вычисляем среднее время для текущего числа процессов
            long totalTime = 0;
            for (int i = 0; i < 3; i++) {
                totalTime += measurements[i];
            }
            double avgParTime = totalTime / 3.0;

            // Ускорение = среднее время последовательного выполнения / среднее время параллельного выполнения
            double speedup = avgSeqTime / avgParTime;

            System.out.printf("%8d | %9.3f | %9.2f | %7.3f | %7.3f | %7.3f\n",
                    numProcesses,
                    avgParTime / 1_000_000.0,
                    speedup,
                    measurements[0] / 1_000_000.0,
                    measurements[1] / 1_000_000.0,
                    measurements[2] / 1_000_000.0
            );
        }

        // очистка файлов с матрицами
        new File(MATRIX_A_FILE).delete();
        new File(MATRIX_B_FILE).delete();
    }

    private static void runChildProcess(String[] args) throws Exception {
        int pid = Integer.parseInt(args[1]);
        int startRow = Integer.parseInt(args[2]);
        int endRow = Integer.parseInt(args[3]);
        int size = Integer.parseInt(args[4]);
        String fileA = args[5];
        String fileB = args[6];
        String outputFile = args[7];

        // загрузка матриц из файлов
        double[][] A = loadMatrixBinary(fileA);
        double[][] B = loadMatrixBinary(fileB);

        // временную матрицу для хранения результатов этого процесса
        double[][] myResult = new double[endRow - startRow][size];

        for (int i = 0; i < endRow - startRow; i++) {
            int actualRow = startRow + i;

            for (int j = 0; j < size; j++) {
                double sum = 0;

                for (int k = 0; k < size; k++) {
                    sum += A[actualRow][k] * B[k][j];
                }

                myResult[i][j] = sum;
            }
        }

        // сохранение результата в файл
        saveResultPart(startRow, endRow, size, myResult, outputFile);
        System.exit(0);
    }

    // перемножение матриц
    private static double[][] multiplySequential(double[][] A, double[][] B) {
        int size = A.length;
        double[][] C = new double[size][size];

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                double sum = 0;
                for (int k = 0; k < size; k++) {
                    sum += A[i][k] * B[k][j];
                }
                C[i][j] = sum;
            }
        }
        return C;
    }

    // сохранение матриц в бин файл
    private static void saveMatrixBinary(double[][] matrix, String filename) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(filename))) {
            int size = matrix.length;
            out.writeInt(size);

            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    out.writeDouble(matrix[i][j]);
                }
            }
        }
    }

    // выгрузка матрицы из файла
    private static double[][] loadMatrixBinary(String filename) throws IOException {
        try (DataInputStream in = new DataInputStream(new FileInputStream(filename))) {
            int size = in.readInt();
            double[][] matrix = new double[size][size];

            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    matrix[i][j] = in.readDouble();
                }
            }
            return matrix;
        }
    }

    // сохранение части инфы в матрицу
    private static void saveResultPart(int startRow, int endRow, int size,
                                       double[][] data, String filename) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(filename))) {
            out.writeInt(startRow);
            out.writeInt(endRow);
            out.writeInt(size);

            for (int i = 0; i < data.length; i++) {
                for (int j = 0; j < size; j++) {
                    out.writeDouble(data[i][j]);
                }
            }
        }
    }

    // выгрузка части инфы из матрицы
    private static void loadResultPart(String filename, double[][] result) throws IOException {
        try (DataInputStream in = new DataInputStream(new FileInputStream(filename))) {
            int startRow = in.readInt();
            int endRow = in.readInt();
            int size = in.readInt();

            for (int i = startRow; i < endRow; i++) {
                for (int j = 0; j < size; j++) {
                    result[i][j] = in.readDouble();
                }
            }
        }
    }
}