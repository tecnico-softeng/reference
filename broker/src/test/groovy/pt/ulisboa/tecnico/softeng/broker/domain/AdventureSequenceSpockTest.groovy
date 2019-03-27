package pt.ulisboa.tecnico.softeng.broker.domain

import pt.ulisboa.tecnico.softeng.broker.domain.Adventure.State
import pt.ulisboa.tecnico.softeng.broker.services.remote.*
import pt.ulisboa.tecnico.softeng.broker.services.remote.dataobjects.RestActivityBookingData
import pt.ulisboa.tecnico.softeng.broker.services.remote.dataobjects.RestRentingData
import pt.ulisboa.tecnico.softeng.broker.services.remote.dataobjects.RestRoomBookingData
import pt.ulisboa.tecnico.softeng.broker.services.remote.exception.*
import spock.lang.Unroll

class AdventureSequenceSpockTest extends SpockRollbackTestAbstractClass {
    def taxInterface
    def bankInterface
    def activityInterface
    def hotelInterface
    def carInterface

    def bookingActivityData
    def bookingRoomData
    def rentingData
    def broker
    def client

    @Override
    def populate4Test() {
        taxInterface = Mock(TaxInterface)
        bankInterface = Mock(BankInterface)
        activityInterface = Mock(ActivityInterface)
        hotelInterface = Mock(HotelInterface)
        carInterface = Mock(CarInterface)

        broker = new Broker("BR01", "eXtremeADVENTURE", BROKER_NIF, BROKER_IBAN,
                activityInterface, hotelInterface, carInterface, bankInterface, taxInterface)
        client = new Client(broker, CLIENT_IBAN, CLIENT_NIF, DRIVING_LICENSE, AGE)

        bookingActivityData = new RestActivityBookingData()
        bookingActivityData.setReference(ACTIVITY_CONFIRMATION)
        bookingActivityData.setPrice(70.0)
        bookingActivityData.setPaymentReference(PAYMENT_CONFIRMATION)
        bookingActivityData.setInvoiceReference(INVOICE_REFERENCE)

        bookingRoomData = new RestRoomBookingData()
        bookingRoomData.setReference(ROOM_CONFIRMATION)
        bookingRoomData.setPrice(80.0)
        bookingRoomData.setPaymentReference(PAYMENT_CONFIRMATION)
        bookingRoomData.setInvoiceReference(INVOICE_REFERENCE)

        rentingData = new RestRentingData()
        rentingData.setReference(RENTING_CONFIRMATION)
        rentingData.setPrice(60.0)
        rentingData.setPaymentReference(PAYMENT_CONFIRMATION)
        rentingData.setInvoiceReference(INVOICE_REFERENCE)
    }

    @Unroll
    def 'success sequence with car #car, hotel #hotel'() {
        given: 'an adventure with rent vehicle as #car'
        def adventure = new Adventure(broker, ARRIVAL, end, client, MARGIN, hotel, car)
        and: 'an activity reservation'
        activityInterface.reserveActivity(_) >> bookingActivityData

        and: 'a room booking'
        if (hotel != Adventure.RoomType.NONE) {
            hotelInterface.reserveRoom(_) >> bookingRoomData
        }
        and: 'a car renting'
        if (car != Adventure.VehicleType.NONE) {
            carInterface.rentCar(*_) >> rentingData
        }

        and: 'a bank payment'
        bankInterface.processPayment(_) >> PAYMENT_CONFIRMATION
        and: 'a tax payment'
        taxInterface.submitInvoice(_) >> INVOICE_DATA
        and: 'the correct return of the data associated with each reservation and payment'
        activityInterface.getActivityReservationData(ACTIVITY_CONFIRMATION) >> bookingActivityData
        if (car != Adventure.VehicleType.NONE) {
            carInterface.getRentingData(RENTING_CONFIRMATION) >> rentingData
        }
        if (hotel != Adventure.RoomType.NONE) {
            hotelInterface.getRoomBookingData(ROOM_CONFIRMATION) >> bookingRoomData
        }
        bankInterface.getOperationData(PAYMENT_CONFIRMATION)

        when: 'the life cycle of the adventure'
        1.upto(cycles) { adventure.process() }

        then: 'the final state is confirmed'
        adventure.getState().getValue() == State.CONFIRMED

        where:
        cycles | car                        | hotel                     | end
        6      | Adventure.VehicleType.CAR  | Adventure.RoomType.SINGLE | DEPARTURE
        5      | Adventure.VehicleType.NONE | Adventure.RoomType.SINGLE | DEPARTURE
        5      | Adventure.VehicleType.CAR  | Adventure.RoomType.NONE   | ARRIVAL
        4      | Adventure.VehicleType.NONE | Adventure.RoomType.NONE   | ARRIVAL
    }

    def 'unsuccess sequence fail activity'() {
        given: 'an adventure'
        def adventure = new Adventure(broker, ARRIVAL, DEPARTURE, client, MARGIN, Adventure.RoomType.SINGLE, Adventure.VehicleType.NONE)
        and: 'an activity exception'
        activityInterface.reserveActivity(_) >> { throw new ActivityException() }

        when: 'the life cycle of the adventure'
        1.upto(2) { adventure.process() }

        then: 'the final state is cancelled'
        State.CANCELLED == adventure.getState().getValue()
    }

    def 'unsuccess sequence fail hotel'() {
        given: 'an adventure'
        def adventure = new Adventure(broker, ARRIVAL, DEPARTURE, client, MARGIN, Adventure.RoomType.SINGLE, Adventure.VehicleType.NONE)
        and: 'an activity reservation'
        activityInterface.reserveActivity(_) >> bookingActivityData
        and: 'an hotel exception'
        hotelInterface.reserveRoom(_) >> { throw new HotelException() }
        and: 'an activity reservation cancelation'
        activityInterface.cancelReservation(ACTIVITY_CONFIRMATION) >> ACTIVITY_CANCELLATION

        when: 'the life cycle of the adventure'
        1.upto(4) { adventure.process() }

        then: 'the final state is cancelled'
        State.CANCELLED == adventure.getState().getValue()
    }

    def 'unsuccess sequence fail car'() {
        given: 'an adventure with rent vehicle'
        def adventure = new Adventure(broker, ARRIVAL, ARRIVAL, client, MARGIN, Adventure.RoomType.NONE, Adventure.VehicleType.MOTORCYCLE)
        and: 'an activity reservation'
        activityInterface.reserveActivity(_) >> bookingActivityData
        and: 'a car exception'
        carInterface.rentCar(*_) >> { throw new CarException() }
        and: 'an activity reservation cancelation'
        activityInterface.cancelReservation(ACTIVITY_CONFIRMATION) >> ACTIVITY_CANCELLATION

        when: 'the life cycle of the adventure'
        1.upto(4) { adventure.process() }

        then: 'the final state is cancelled'
        State.CANCELLED == adventure.getState().getValue()
    }

    def 'unsuccess sequence fail payment'() {
        given: 'an adventure with rent vehicle'
        def adventure = new Adventure(broker, ARRIVAL, DEPARTURE, client, MARGIN, Adventure.RoomType.DOUBLE, Adventure.VehicleType.MOTORCYCLE)
        and: 'an activity reservation'
        activityInterface.reserveActivity(_) >> bookingActivityData
        and: 'a room booking'
        hotelInterface.reserveRoom(_) >> bookingRoomData
        and: 'a car rental'
        carInterface.rentCar(*_) >> rentingData
        and: 'a bank exception'
        bankInterface.processPayment(_) >> { throw new BankException() }
        and: 'the correct return of the data associated with each cancelation'
        activityInterface.cancelReservation(_) >> ACTIVITY_CANCELLATION
        hotelInterface.cancelBooking(ROOM_CONFIRMATION) >> ROOM_CANCELLATION
        carInterface.cancelRenting(RENTING_CONFIRMATION) >> RENTING_CANCELLATION

        when: 'the life cycle of the adventure'
        1.upto(6) { adventure.process() }

        then: 'the final state is cancelled'
        State.CANCELLED == adventure.getState().getValue()
    }

    def 'unsuccess sequence fail tax'() {
        given: 'an adventure with rent vehicle'
        def adventure = new Adventure(broker, ARRIVAL, DEPARTURE, client, MARGIN, Adventure.RoomType.DOUBLE, Adventure.VehicleType.MOTORCYCLE)
        and: 'an activity reservation'
        activityInterface.reserveActivity(_) >> bookingActivityData
        and: 'a room booking'
        hotelInterface.reserveRoom(_) >> bookingRoomData
        and: 'a car rental'
        carInterface.rentCar(Adventure.VehicleType.MOTORCYCLE, *_) >> rentingData
        and: 'a bank payment'
        bankInterface.processPayment(_) >> PAYMENT_CONFIRMATION
        and: 'a tax exception'
        taxInterface.submitInvoice(_) >> { throw new TaxException() }
        and: 'the correct return of the data associated with each cancelation'
        activityInterface.cancelReservation(ACTIVITY_CONFIRMATION) >> ACTIVITY_CANCELLATION
        hotelInterface.cancelBooking(ROOM_CONFIRMATION) >> ROOM_CANCELLATION
        carInterface.cancelRenting(RENTING_CONFIRMATION) >> RENTING_CANCELLATION
        bankInterface.cancelPayment(PAYMENT_CONFIRMATION) >> PAYMENT_CANCELLATION

        when: 'the life cycle of the adventure'
        1.upto(6) { adventure.process() }

        then: 'the final state is cancelled'
        State.CANCELLED == adventure.getState().getValue()
    }
}
