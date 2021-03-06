package ru.practicum.shareit.booking;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.practicum.shareit.exceptions.ElementNotFoundException;
import ru.practicum.shareit.exceptions.ValidationException;
import ru.practicum.shareit.item.Item;
import ru.practicum.shareit.item.ItemRepository;
import ru.practicum.shareit.item.Status;
import ru.practicum.shareit.user.User;
import ru.practicum.shareit.user.UserRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Service
@Slf4j
public class BookingServiceImpl implements BookingService {
    private final UserRepository userRepository;
    private final ItemRepository itemRepository;
    private final BookingRepository bookingRepository;

    @Autowired
    public BookingServiceImpl(UserRepository userRepository, ItemRepository itemRepository,
                              BookingRepository bookingRepository) {
        this.userRepository = userRepository;
        this.itemRepository = itemRepository;
        this.bookingRepository = bookingRepository;
    }

    @Override
    public Booking addBooking(long userId, Booking booking) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ElementNotFoundException(
                String.format("пользователь с таким id%d.", userId)));
        Item item = itemRepository.findById(booking.getItemId()).orElseThrow(() -> new ElementNotFoundException(
                String.format("вещь с id%d.", booking.getItemId())));
        booking.setItem(item);
        if (user.equals(booking.getItem().getOwner())) {
            log.error("Владелец вещи не может арендовать сам у себя.");
            throw new ElementNotFoundException("Владелец вещи не может арендовать сам у себя.");
        }
        booking.setBooker(user);
        if (!booking.getItem().getIsAvailable()) {
            log.error("Бронирование вещи id{} недоступно.", booking.getItem().getId());
            throw new ValidationException(String.format("бронирование вещи id%d недоступно.",
                    booking.getItem().getId()));
        }
        if (booking.getStart().isBefore(LocalDateTime.now())) {
            log.error("Время начала бронирования в прошлом.");
            throw new ValidationException("время начала бронирования в прошлом.");
        }
        if (booking.getEnd().isBefore(LocalDateTime.now())) {
            log.error("Время окончания бронирования в прошлом.");
            throw new ValidationException("время окончания бронирования в прошлом.");
        }
        if (booking.getStart().isAfter(booking.getEnd())) {
            log.error("Время начала бронирования позже времени окончания бронирования.");
            throw new ValidationException("время начала бронирования позже времени окончания бронирования.");
        }
        booking.setStatus(Status.WAITING);
        log.info("Добавлено бронирование вещи id{}.", item.getId());
        return bookingRepository.save(booking);
    }

    @Override
    public Booking setApprovedByOwner(long userId, long bookingId, boolean approved) {
        if (!userRepository.existsById(userId)) {
            log.error("Пользователь id{} не найден.", userId);
            throw new ElementNotFoundException(String.format("пользователь с таким id%d.", userId));
        }
        Booking booking = bookingRepository.findById(bookingId).orElseThrow(() -> new ElementNotFoundException(
                String.format("бронирование с таким id%d.", bookingId)));
        if (booking.getItem().getOwner().getId() != userId) {
            if (booking.getBooker().getId() == userId) {
                log.error("Арендатор id{} не имеет доступа для изменения статуса бронирования id{}.", userId,
                        bookingId);
                throw new ElementNotFoundException(String.format("арендатор id%d не имеет доступа для изменения " +
                        "статуса бронирования id%d.", userId, bookingId));
            }
            log.error("Пользователь id{} не имеет доступа для изменения статуса бронирования id{}.", userId,
                    bookingId);
            throw new ValidationException(String.format("пользователь id%d не имеет доступа для изменения " +
                    "статуса бронирования id%d.", userId, bookingId));
        }
        if (approved) {
            if (booking.getStatus().equals(Status.APPROVED)) {
                log.error("Повторное изменение статуса на идентичный не допускается.");
                throw new ValidationException("Повторное изменение статуса на идентичный не допускается.");
            }
            booking.setStatus(Status.APPROVED);
            log.info("Подтверждено бронирование id{} вещи id{}.", bookingId, booking.getItem().getId());
        } else {
            if (booking.getStatus().equals(Status.REJECTED)) {
                log.error("Повторное изменение статуса на идентичный не допускается.");
                throw new ValidationException("Повторное изменение статуса на идентичный не допускается.");
            }
            booking.setStatus(Status.REJECTED);
            log.info("Отклонено бронирование id{} вещи id{}.", bookingId, booking.getItem().getId());
        }
        return bookingRepository.save(booking);
    }

    @Override
    public Booking getBookingById(long userId, long bookingId) {
        if (!userRepository.existsById(userId)) {
            log.error("Пользователь id{} не найден.", userId);
            throw new ElementNotFoundException(String.format("пользователь с таким id%d.", userId));
        }
        Booking booking = bookingRepository.findById(bookingId).orElseThrow(() -> new ElementNotFoundException(
                String.format("бронирование с таким id%d.", bookingId)));
        if (booking.getBooker().getId() == userId || booking.getItem().getOwner().getId() == userId) {
            return booking;
        } else {
            throw new ElementNotFoundException(String.format("пользователь id%d не является владельцем или " +
                    "арендатором вещи.", userId));
        }
    }

    @Override
    public Collection<Booking> getAllBookingsByUserId(long bookerId, String status) {
        if (!userRepository.existsById(bookerId)) {
            log.error("Пользователь id{} не найден.", bookerId);
            throw new ElementNotFoundException(String.format("арендатор с таким id%d.", bookerId));
        }
        String status1 = status.toUpperCase();
        if (status1.equals("ALL")) {
            log.info("Запрошен список всех бронирований арендатора id{}.", bookerId);
            return bookingRepository.findBookingsByBooker_Id(bookerId);
        } else {
            if (!List.of("CURRENT", "PAST", "FUTURE", "WAITING", "REJECTED").contains(status1)) {
                log.error("Введено неверное значение статуса {}.", status1);
                throw new ValidationException("значение статуса может быть только CURRENT, PAST, FUTURE, WAITING, " +
                        "REJECTED");
            }
            log.info("Запрошен список бронирований арендатора id{} со статусом {}.", bookerId, status1);
            return bookingRepository.findBookingsByBooker_IdAndStatus(bookerId, Status.valueOf(status1));
        }
    }

    @Override
    public Collection<Booking> getAllBookingsByOwnerId(long ownerId, String status) {
        if (!userRepository.existsById(ownerId)) {
            log.error("Пользователь id{} не найден.", ownerId);
            throw new ElementNotFoundException(String.format("пользователь с таким id%d.", ownerId));
        }
        String status1 = status.toUpperCase();
        if (status1.equals("ALL")) {
            log.info("Запрошен список всех бронирований владельца id{}.", ownerId);
            return bookingRepository.findBookingsByOwnerId(ownerId);
        } else {
            if (!List.of("CURRENT", "PAST", "FUTURE", "WAITING", "REJECTED").contains(status1)) {
                log.error("Введено неверное значение статуса {}.", status1);
                throw new ValidationException("значение статуса может быть только CURRENT, PAST, FUTURE, WAITING, " +
                        "REJECTED");
            }
            log.info("Запрошен список бронирований владельца id{} со статусом {}.", ownerId, status1);
            return bookingRepository.findBookingsByOwnerIdAndStatus(ownerId, Status.valueOf(status1));
        }
    }
}
