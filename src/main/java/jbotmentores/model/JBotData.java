package jbotmentores.model;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

@Component
public class JBotData {

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();


    // TODO colocar como private
    public Set<Mentor> mentores = new HashSet<>();

    /*
        private Mentor mentorByEmail(String email) {
            try {
                readLock.lock();
                Mentor mentor = this.mentorsByEmail.get(email);
                return mentor;
            } finally {
                readLock.unlock();
            }
        }
    */
    public List<Mentor> listMentoresBySkill(String skill) {
        return this.getMentores()
                .stream()
                .filter(Objects::nonNull)
                .filter(m -> m.getSkills().stream().anyMatch(s -> s.getName().toLowerCase().contains(skill.toLowerCase())))
                .collect(Collectors.toList());
    }

    public Set<Mentor> getMentores() {
        try {
            readLock.lock();
            return new HashSet<>(this.mentores);
        } finally {
            readLock.unlock();
        }
    }

    private void clearAll() {
        try {
            writeLock.lock();
            this.mentores.clear();
        } finally {
            writeLock.unlock();
        }
    }

    private void processData(Map<Integer, String> data) {
        try {
            writeLock.lock();

            var name = data.get(0);
            var email = data.get(1);

            String[] skillsData =
                    data.getOrDefault(2, "").replaceAll("\\n", " ").replaceAll("/", ",").split(",");
            var skills = Arrays
                    .stream(skillsData).map(String::trim).map(Skill::new)
                    .collect(Collectors.toSet());

            String dia22 = data.getOrDefault(3, "");
            Set<Slot> slots = new TreeSet<>(loadDay(LocalDate.of(2021, 10, 22), dia22));
            String dia23 = data.getOrDefault(4, "");
            slots.addAll(loadDay(LocalDate.of(2021, 10, 23), dia23));
            String dia24 = data.getOrDefault(5, "");
            slots.addAll(loadDay(LocalDate.of(2021, 10, 24), dia24));

            Mentor mentor = new Mentor(name, email, skills, slots);
            mentores.add(mentor);

        } finally {
            writeLock.unlock();
        }
    }


    private List<Slot> loadDay(
            LocalDate day,
            String dia22) {
        String[] slotData =
                dia22.replaceAll("\\*nãoestoudisponívelnessedia\\*", "")
                        .replaceAll(" ", "")
                        .replaceAll("24h", "23h59")
                        .split(",");
        return
                Arrays.stream(slotData)
                        .map(String::trim)
                        .filter(d -> !"*nãoestoudisponívelnessedia*".equals(d)).map(sd -> {
                            var horas = sd.split("-");
                            var from = Slot.parseToLocalDateTime(day, horas[0]);
                            var to = Slot.parseToLocalDateTime(day, horas.length > 1 ? horas[1] : horas[0]);
                            return new Slot(from, to);
                        })
                        .collect(Collectors.toList());
    }


    public void updateDataFrom(InputStream inputStream) throws IOException {
        try (
                Workbook wb = new XSSFWorkbook(inputStream)
        ) {
            writeLock.lock();
            clearAll();
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                Sheet sheet = wb.getSheetAt(i);
                System.out.println(wb.getSheetName(i));

                for (Row row : sheet) {
                    if (row.getRowNum() > 1) {
                        System.out.println("rownum: " + row.getRowNum());
                        Map<Integer, String> data = new LinkedHashMap<>();
                        for (Cell cell : row) {
                            if (CellType.STRING.equals(cell.getCellType())) {
                                data.put(cell.getAddress().getColumn(), cell.getStringCellValue());
                            } else if (CellType.NUMERIC.equals(cell.getCellType())) {
                                data.put(cell.getAddress().getColumn(), "" + cell.getNumericCellValue());
                            }
                        }
                        try {
                            processData(data);
                        } catch (RuntimeException ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            }
        } finally {
            writeLock.unlock();
        }
    }

}
