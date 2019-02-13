package pt.ulisboa.tecnico.softeng.hotel.domain
import static org.junit.Assert.*

import pt.ulisboa.tecnico.softeng.hotel.domain.Room.Type
import pt.ulisboa.tecnico.softeng.hotel.exception.HotelException
import spock.lang.Unroll

class RoomConstructorMethodSpockTest extends SpockRollbackTestAbstractClass {
	private Hotel hotel

	@Override
	public Object populate4Test() {
		this.hotel = new Hotel("XPTO123", "Lisboa", "NIF", "IBAN", 20.0, 30.0)
	}

	def "success"() {
		when: "create a room"
		Room room = new Room(this.hotel, "01", Type.DOUBLE)

		then: "all fieds are correctly set"
		this.hotel == room.getHotel()
		room.getNumber().equals("01")
		room.getType() == Type.DOUBLE
		this.hotel.getRoomSet().size() == 1
	}

	@Unroll('one of the arguments is invalid: #hotel | #number | #type')
	def "incorrect input arguments"() {
		when: "create a room with incorrect arguments"
		new Room(hotel, number, type)

		then: "a HotelException is thrown"
		def error = thrown(HotelException)

		where:
		hotel | number | type
		null | "01" | Type.DOUBLE
		this.hotel | null | Type.DOUBLE
		this.hotel | "" | Type.DOUBLE
		this.hotel | "     " | Type.DOUBLE
		this.hotel | "JOSE" | Type.DOUBLE
		this.hotel | "01" | null
	}

	def "not unique room number"() {
		given: "a room"
		new Room(this.hotel, "01", Type.SINGLE);

		when: "another room is created with the same number"
		new Room(this.hotel, "01", Type.DOUBLE)

		then: "a HotelException is thrown"
		def error = thrown(HotelException)
		this.hotel.getRoomSet().size() == 1
	}
}
